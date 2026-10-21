package com.wms.system.service.inventory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.system.dto.inventory.ArrangePackagingRequest;
import com.wms.system.dto.inventory.PackagingView;
import com.wms.system.entity.*;
import com.wms.system.entity.enums.InventoryQualityStatus;
import com.wms.system.entity.enums.OutboundTaskStatus;
import com.wms.system.entity.enums.SourceType;
import com.wms.system.exception.BusinessException;
import com.wms.system.repository.*;
import com.wms.system.security.AuthUserResolver;
import com.wms.system.service.*;
import com.wms.system.tenant.context.CompanyScope;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryPackagingService {
    private final InventoryBatchRepository batches;
    private final OutboundTaskRepository outboundTasks;
    private final LocationRepository locations;
    private final InventoryPostingService posting;
    private final LocationOccupancyService occupancy;
    private final WarehouseScopeService warehouseScope;
    private final DomainOutboxService outbox;
    private final IdempotencyService idempotency;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public PackagingView get(Long id, Authentication authentication) {
        InventoryBatch batch = batches.findById(id).orElseThrow(() -> new BusinessException("BATCH_NOT_FOUND"));
        requireAccess(batch, authentication);
        return PackagingView.of(batch);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<PackagingView> arrange(ArrangePackagingRequest request, Authentication authentication,
                                       IdempotencyRequest claim, String key) {
        List<Long> ids = request.sources().stream().map(ArrangePackagingRequest.Source::batchId).sorted().toList();
        if (new HashSet<>(ids).size() != ids.size()) throw new BusinessException("PACKAGING_INVALID");
        List<InventoryBatch> sources = batches.findAllByIdForPosting(ids);
        if (sources.size() != ids.size()) throw new BusinessException("BATCH_NOT_FOUND");
        if (outboundTasks.existsByAssignedBatchIdInAndStatusIn(ids,
            List.of(OutboundTaskStatus.PENDING, OutboundTaskStatus.PICKING, OutboundTaskStatus.QUALITY_EXCEPTION)))
            throw new BusinessException("PACKAGING_RESERVED");
        InventoryBatch template = sources.get(0);
        requireAccess(template, authentication);
        int packSize = PackagingView.of(template).standardPackSize();
        List<PackagingView> before = sources.stream().map(PackagingView::of).toList();
        long quantityBefore = 0;
        long sealedBefore = 0;
        for (InventoryBatch source : sources) {
            requireAccess(source, authentication);
            int expectedVersion = request.sources().stream().filter(s -> s.batchId().equals(source.getId()))
                .findFirst().orElseThrow().version();
            if (!Objects.equals(source.getVersion(), expectedVersion)) throw new BusinessException("PACKAGING_STALE");
            if (source.getStocktakeTaskId() != null) throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
            if (source.getReservedQuantity() != null && source.getReservedQuantity() > 0)
                throw new BusinessException("PACKAGING_RESERVED");
            if (request.mode() == ArrangePackagingRequest.Mode.ARRANGE) requireMutable(source);
            if (!sameLot(template, source) || PackagingView.of(source).standardPackSize() != packSize)
                throw new BusinessException("PACKAGING_LOT_MISMATCH");
            if (request.mode() == ArrangePackagingRequest.Mode.ARRANGE && source.getPackaging() == null)
                throw new BusinessException("PACKAGING_VERIFY_REQUIRED");
            quantityBefore += source.getQuantity();
            if (source.getPackaging() != null) sealedBefore += source.getPackaging().sealedBoxes();
        }
        Map<Long, StockPackaging> desired = new LinkedHashMap<>();
        long quantityAfter = 0;
        long sealedAfter = 0;
        for (var result : request.results()) {
            if (result.packaging().packSize() != packSize || desired.putIfAbsent(result.locationId(), result.packaging()) != null)
                throw new BusinessException("PACKAGING_INVALID");
            quantityAfter += result.packaging().total();
            sealedAfter += result.packaging().sealedBoxes();
        }
        if (quantityBefore != quantityAfter) throw new BusinessException("PACKAGING_TOTAL_MISMATCH",
            Map.of("expected", quantityBefore, "actual", quantityAfter));
        if (request.mode() == ArrangePackagingRequest.Mode.ARRANGE && sealedAfter > sealedBefore)
            throw new BusinessException("PACKAGING_CANNOT_RESEAL");
        if (request.mode() == ArrangePackagingRequest.Mode.VERIFY) {
            if (sources.size() != 1 || desired.size() != 1 || template.getPackaging() != null
                || !desired.containsKey(template.getLocation().getId())) throw new BusinessException("PACKAGING_INVALID");
        }
        // Every location is locked in the same order before occupancy checks or creating a balance.
        Set<Long> locationIds = new TreeSet<>(desired.keySet());
        sources.forEach(s -> locationIds.add(s.getLocation().getId()));
        Map<Long, Location> lockedLocations = new LinkedHashMap<>();
        for (Long id : locationIds) {
            Location location = locations.findByIdForInventoryPosting(id)
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND"));
            if (!Objects.equals(location.getCompanyId(), CompanyScope.currentCompanyId())
                || !Objects.equals(location.getWarehouse().getId(), template.getLocation().getWarehouse().getId())
                || !Boolean.TRUE.equals(location.getEnabled())) throw new BusinessException("PACKAGING_LOCATION_INVALID");
            if (request.mode() == ArrangePackagingRequest.Mode.ARRANGE)
                occupancy.validateCanStore(location, template.getProductSku(), template.getBatchCode());
            // Never accidentally overwrite a target box or bypass its reservation/counting lock.
            if (request.mode() == ArrangePackagingRequest.Mode.ARRANGE && desired.containsKey(id) && batches.findPositiveActiveBatchesByLocationId(id).stream()
                .anyMatch(b -> !ids.contains(b.getId()))) throw new BusinessException("PACKAGING_TARGET_NOT_SELECTED");
            lockedLocations.put(id, location);
        }
        Map<Long, InventoryBatch> primary = new LinkedHashMap<>();
        sources.forEach(source -> primary.putIfAbsent(source.getLocation().getId(), source));
        List<InventoryBatch> affected = new ArrayList<>(sources);
        for (var result : desired.entrySet()) {
            if (!primary.containsKey(result.getKey()) && result.getValue().total() > 0) {
                Location target = lockedLocations.get(result.getKey());
                var previous = batches.findByBatchCodeAndLocationCode(template.getBatchCode(), target.getLocationCode());
                if (previous.isPresent()) {
                    InventoryBatch empty = batches.findByIdForPosting(previous.get().getId()).orElseThrow();
                    if (!sameLot(template, empty) || empty.getQuantity() != 0
                        || empty.getStocktakeTaskId() != null
                        || (empty.getReservedQuantity() != null && empty.getReservedQuantity() != 0))
                        throw new BusinessException("PACKAGING_TARGET_NOT_SELECTED");
                    empty.setActive(true);
                    primary.put(result.getKey(), empty);
                    affected.add(empty);
                    continue;
                }
                InventoryBatch created = InventoryBatch.builder().batchCode(template.getBatchCode())
                    .productSku(template.getProductSku()).purchaseOrderItem(template.getPurchaseOrderItem())
                    .location(target).locationCode(target.getLocationCode()).quantity(0)
                    .initialQuantity(result.getValue().total()).productionDate(template.getProductionDate())
                    .expiryDate(template.getExpiryDate()).externalBatchCode(template.getExternalBatchCode())
                    .entryDate(template.getEntryDate()).qualityStatus(template.getQualityStatus())
                    .packaging(StockPackaging.empty(packSize)).active(true).build();
                created.setCompanyId(CompanyScope.currentCompanyId());
                created = batches.saveAndFlush(created);
                primary.put(result.getKey(), created);
                affected.add(created);
            }
        }
        for (InventoryBatch batch : affected) {
            StockPackaging after = primary.get(batch.getLocation().getId()) == batch
                ? desired.getOrDefault(batch.getLocation().getId(), StockPackaging.empty(packSize))
                : StockPackaging.empty(packSize);
            int delta = after.total() - batch.getQuantity();
            posting.post(new InventoryPostingCommand(batch.getId(), batch.getProductSku().getId(),
                batch.getLocation().getId(), delta,
                new PostingContext(CompanyScope.currentCompanyId(), batch.getLocation().getWarehouse().getId(),
                    delta < 0 ? SourceType.TRANSFER_OUT : SourceType.TRANSFER_IN,
                    "PACKAGING-" + template.getId(), "PACKAGING_ARRANGE", null,
                    AuthUserResolver.resolveUserId(authentication), AuthUserResolver.resolveUsername(authentication),
                    "PACKAGING:" + claim.getId() + ":" + batch.getId(), key), after));
        }
        batches.flush();
        locationIds.forEach(occupancy::refreshLocationStatus);
        List<PackagingView> after = affected.stream().map(PackagingView::of).toList();
        outbox.append("INVENTORY_PACKAGING_ARRANGED", "InventoryBatch", template.getId(),
            Map.of("operatorId", AuthUserResolver.resolveUserId(authentication), "mode", request.mode(),
                "before", before, "after", after));
        try { idempotency.markSucceeded(claim, 200, mapper.writeValueAsString(after)); }
        catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        return after;
    }

    public static void requireMutable(InventoryBatch batch) {
        if (batch.getStocktakeTaskId() != null) throw new BusinessException("PACKAGING_STOCKTAKE_LOCKED");
        if (batch.getReservedQuantity() != null && batch.getReservedQuantity() > 0)
            throw new BusinessException("PACKAGING_RESERVED");
        if (!batch.isActive() || batch.getQuantity() <= 0 || batch.getQualityStatus() != InventoryQualityStatus.AVAILABLE
            || batch.isExpired()) throw new BusinessException("INVENTORY_BATCH_NOT_SALEABLE");
    }

    private boolean sameLot(InventoryBatch a, InventoryBatch b) {
        return Objects.equals(a.getCompanyId(), b.getCompanyId())
            && Objects.equals(a.getProductSku().getId(), b.getProductSku().getId())
            && Objects.equals(a.getBatchCode(), b.getBatchCode())
            && Objects.equals(a.getExternalBatchCode(), b.getExternalBatchCode())
            && Objects.equals(a.getProductionDate(), b.getProductionDate())
            && Objects.equals(a.getExpiryDate(), b.getExpiryDate())
            && Objects.equals(a.getQualityStatus(), b.getQualityStatus())
            && Objects.equals(a.getLocation().getWarehouse().getId(), b.getLocation().getWarehouse().getId());
    }

    private void requireAccess(InventoryBatch batch, Authentication authentication) {
        if (!Objects.equals(batch.getCompanyId(), CompanyScope.currentCompanyId()) || batch.getLocation() == null)
            throw new BusinessException("BATCH_NOT_FOUND");
        warehouseScope.requireAccess(authentication, List.of(batch.getLocation().getWarehouse().getId()), "BATCH", batch.getId());
    }
}

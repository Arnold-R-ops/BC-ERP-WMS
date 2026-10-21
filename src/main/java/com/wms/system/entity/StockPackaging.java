package com.wms.system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wms.system.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Actual composition of one batch/location balance. No carton identity or labels. */
public record StockPackaging(Integer packSize, Integer sealedBoxes, List<Integer> openedQuantities) {
    public StockPackaging {
        if (packSize == null || packSize < 1 || sealedBoxes == null || sealedBoxes < 0
            || openedQuantities == null || openedQuantities.stream().anyMatch(q -> q == null || q <= 0)) {
            throw new BusinessException("PACKAGING_INVALID");
        }
        openedQuantities = List.copyOf(openedQuantities);
        long total = (long) packSize * sealedBoxes;
        for (int quantity : openedQuantities) total += quantity;
        if (total > Integer.MAX_VALUE) throw new BusinessException("PACKAGING_INVALID");
    }

    @JsonIgnore
    public int total() {
        return Math.toIntExact((long) packSize * sealedBoxes
            + openedQuantities.stream().mapToLong(Integer::longValue).sum());
    }

    public void requireTotal(int quantity) {
        if (total() != quantity) throw new BusinessException("PACKAGING_TOTAL_MISMATCH",
            Map.of("expected", quantity, "actual", total()));
    }

    public static StockPackaging empty(int packSize) {
        return new StockPackaging(packSize, 0, List.of());
    }

    /** Selection indices refer to the supplied batch version, never to a physical box number. */
    public record Selection(Integer version, Integer sealedBoxes, List<Integer> openedQuantities,
                            List<Integer> takeFromNewlyOpened) {}

    public record Taken(StockPackaging remaining, StockPackaging taken) {}

    public Taken take(Selection selection, int version, int quantity) {
        if (selection == null) throw new BusinessException("PACKAGING_SELECTION_REQUIRED");
        if (selection.version() == null || selection.version() != version)
            throw new BusinessException("PACKAGING_STALE");
        if (selection.sealedBoxes() == null || selection.sealedBoxes() < 0
            || selection.openedQuantities() == null
            || selection.openedQuantities().size() != openedQuantities.size())
            throw new BusinessException("PACKAGING_INVALID");
        List<Integer> newlyOpened = selection.takeFromNewlyOpened() == null
            ? List.of() : selection.takeFromNewlyOpened();
        if ((long) selection.sealedBoxes() + newlyOpened.size() > sealedBoxes)
            throw new BusinessException("PACKAGING_INVALID");
        List<Integer> remaining = new ArrayList<>();
        List<Integer> taken = new ArrayList<>();
        for (int i = 0; i < openedQuantities.size(); i++) {
            Integer amount = selection.openedQuantities().get(i);
            int before = openedQuantities.get(i);
            if (amount == null || amount < 0 || amount > before) throw new BusinessException("PACKAGING_INVALID");
            if (before > amount) remaining.add(before - amount);
            if (amount > 0) taken.add(amount);
        }
        for (Integer amount : newlyOpened) {
            if (amount == null || amount <= 0 || amount >= packSize) throw new BusinessException("PACKAGING_INVALID");
            remaining.add(packSize - amount);
            taken.add(amount);
        }
        StockPackaging issued = new StockPackaging(packSize, selection.sealedBoxes(), taken);
        issued.requireTotal(quantity);
        return new Taken(new StockPackaging(packSize,
            sealedBoxes - selection.sealedBoxes() - newlyOpened.size(), remaining), issued);
    }
}

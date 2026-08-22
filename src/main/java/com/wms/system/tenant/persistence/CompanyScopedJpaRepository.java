package com.wms.system.tenant.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.annotations.TenantId;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Prevents direct primary-key loads from bypassing Hibernate's company filter.
 *
 * <p>Hibernate discriminator filters protect JPQL/criteria/derived queries but
 * {@code EntityManager.find()} is a direct identifier lookup. Spring Data's
 * default {@code findById()} uses that lookup, so tenant-owned entities are
 * deliberately loaded through a criteria query instead.</p>
 */
@Transactional(readOnly = true)
public class CompanyScopedJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager entityManager;
    private final boolean tenantOwned;

    public CompanyScopedJpaRepository(
            JpaEntityInformation<T, ?> entityInformation,
            EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
        this.tenantOwned = declaresTenantId(entityInformation.getJavaType());
    }

    @Override
    public Optional<T> findById(ID id) {
        if (!tenantOwned) {
            return super.findById(id);
        }

        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = builder.createQuery(entityInformation.getJavaType());
        Root<T> root = query.from(entityInformation.getJavaType());
        query.select(root).where(builder.equal(
            root.get(entityInformation.getRequiredIdAttribute().getName()), id));
        return entityManager.createQuery(query).setMaxResults(1).getResultStream().findFirst();
    }

    @Override
    public T getReferenceById(ID id) {
        if (!tenantOwned) {
            return super.getReferenceById(id);
        }
        return findById(id).orElseThrow(() -> new EntityNotFoundException(
            entityInformation.getEntityName() + " not found"));
    }

    @Override
    public T getById(ID id) {
        return getReferenceById(id);
    }

    @Override
    public T getOne(ID id) {
        return getReferenceById(id);
    }

    private static boolean declaresTenantId(Class<?> type) {
        for (Class<?> candidate = type;
                candidate != null && candidate != Object.class;
                candidate = candidate.getSuperclass()) {
            for (Field field : candidate.getDeclaredFields()) {
                if (field.isAnnotationPresent(TenantId.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}

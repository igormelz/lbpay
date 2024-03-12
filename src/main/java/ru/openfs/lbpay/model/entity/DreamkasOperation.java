package ru.openfs.lbpay.model.entity;

import java.time.LocalDateTime;
import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import ru.openfs.lbpay.dto.dreamkas.type.OperationStatus;

@Entity
public class DreamkasOperation extends PanacheEntity {
    /** dk opertion id */
    public String operationId;
    /** dk operation status */
    @Enumerated(EnumType.STRING)
    public OperationStatus operationStatus;
    /** dk external id */
    public String externalId;
    /** billing order number */
    public String orderNumber;
    /** billing account */
    public String account;
    /** payment amount */
    public Double amount;
    /** customer info.email */
    public String email;
    /** customer info.phone */
    public String phone;
    /** timestamp created record */
    public LocalDateTime createAt = LocalDateTime.now();

    public static Optional<DreamkasOperation> findByExternalId(String externalId) {
        return find("externalId", externalId).firstResultOptional();
    }

    public static Optional<DreamkasOperation> findByOrderNumber(String orderNumber) {
        return find("orderNumber", orderNumber).firstResultOptional();
    }
}

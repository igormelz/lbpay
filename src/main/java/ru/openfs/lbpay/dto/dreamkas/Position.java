package ru.openfs.lbpay.dto.dreamkas;

import ru.openfs.lbpay.dto.dreamkas.type.PositionType;
import ru.openfs.lbpay.dto.dreamkas.type.VatType;

public record Position(
    String name, 
    PositionType type, 
    Integer quantity, 
    /** цена в КОПЕЙКАХ за единицу расчета */
    Integer price, 
    /** цена позиции в КОПЕЙКАХ за единицу расчета */
    Integer priceSum,
    VatType tax,
    /** Сумма НДС (если не указан, то будет вычислен устройством) */
    Integer taxSum
){}
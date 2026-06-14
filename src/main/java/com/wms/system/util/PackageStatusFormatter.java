package com.wms.system.util;

public final class PackageStatusFormatter {

    private PackageStatusFormatter() {
    }

    public static String plain(Number quantityValue, Number packSizeValue) {
        return switch (composition(quantityValue, packSizeValue)) {
            case FULL_PACK -> "整箱";
            case LOOSE -> "散货";
            case MIXED -> "整散混合";
        };
    }

    public static String withIcon(Number quantityValue, Number packSizeValue) {
        return switch (composition(quantityValue, packSizeValue)) {
            case FULL_PACK -> "📦 整箱";
            case LOOSE -> "📥 散货";
            case MIXED -> "📦📥 整散混合";
        };
    }

    private static Composition composition(Number quantityValue, Number packSizeValue) {
        int quantity = quantityValue == null ? 0 : quantityValue.intValue();
        int packSize = packSizeValue == null ? 1 : Math.max(packSizeValue.intValue(), 1);

        if (quantity < packSize) {
            return Composition.LOOSE;
        }
        if (quantity % packSize == 0) {
            return Composition.FULL_PACK;
        }
        return Composition.MIXED;
    }

    private enum Composition {
        FULL_PACK,
        LOOSE,
        MIXED
    }
}

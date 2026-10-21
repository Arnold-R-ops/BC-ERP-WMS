package com.wms.system.entity;

import com.wms.system.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class StockPackagingTest {
    @Test void keepsTwoOpenedBoxesEvenWhenTheirSumEqualsAStandardBox() {
        var p = new StockPackaging(12, 1, List.of(6, 6));
        assertThat(p.total()).isEqualTo(24);
        assertThat(p.sealedBoxes()).isEqualTo(1);
        assertThat(p.openedQuantities()).containsExactly(6, 6);
        var taken = p.take(new StockPackaging.Selection(4, 0, List.of(4, 0), List.of()), 4, 4);
        assertThat(taken.remaining().openedQuantities()).containsExactly(2, 6);
        assertThat(taken.remaining().sealedBoxes()).isEqualTo(1);
    }
    @Test void anOverStandardOpenedBoxRemainsOneOpenedBoxAfterPicking() {
        var p = new StockPackaging(12, 0, List.of(17));
        var taken = p.take(new StockPackaging.Selection(2, 0, List.of(12), List.of()), 2, 12);
        assertThat(taken.remaining()).isEqualTo(new StockPackaging(12, 0, List.of(5)));
        assertThat(taken.taken()).isEqualTo(new StockPackaging(12, 0, List.of(12)));
        assertThat(taken.remaining().total() + taken.taken().total()).isEqualTo(17);
    }
    @Test void opensOnlyTheSelectedUnopenedBoxAndDeductsOnlyWhatLeaves() {
        var p = new StockPackaging(12, 2, List.of(9, 8));
        var taken = p.take(new StockPackaging.Selection(7, 0, List.of(0, 0), List.of(3)), 7, 3);
        assertThat(taken.remaining()).isEqualTo(new StockPackaging(12, 1, List.of(9, 8, 9)));
        assertThat(taken.remaining().total()).isEqualTo(p.total() - 3);
        assertThat(taken.taken().total()).isEqualTo(3);
    }
    @Test void movesAWholeOpenedBalanceWithoutSplittingItByStandardSize() {
        var p = new StockPackaging(12, 0, List.of(9, 17));
        var taken = p.take(new StockPackaging.Selection(1, 0, List.of(0, 17), List.of()), 1, 17);
        assertThat(taken.remaining().openedQuantities()).containsExactly(9);
        assertThat(taken.taken().openedQuantities()).containsExactly(17);
    }
    @Test void refusesStaleSelectionsInsteadOfApplyingAnIndexToAnotherBox() {
        var p = new StockPackaging(12, 0, List.of(8));
        assertThatThrownBy(() -> p.take(new StockPackaging.Selection(2, 0, List.of(4), List.of()), 3, 4))
            .isInstanceOf(BusinessException.class).hasMessage("PACKAGING_STALE");
    }
    @Test void refusesOverPickingAndQuantityMismatch() {
        var p = new StockPackaging(12, 1, List.of(9));
        assertThatThrownBy(() -> p.take(new StockPackaging.Selection(2, 0, List.of(10), List.of()), 2, 10))
            .hasMessage("PACKAGING_INVALID");
        assertThatThrownBy(() -> p.take(new StockPackaging.Selection(2, 1, List.of(0), List.of(3)), 2, 15))
            .hasMessage("PACKAGING_INVALID");
        assertThatThrownBy(() -> p.take(new StockPackaging.Selection(2, 0, List.of(4), List.of()), 2, 5))
            .hasMessage("PACKAGING_TOTAL_MISMATCH");
    }
    @Test void validatesInputWithoutCappingOpenedQuantitiesAtTheStandardSize() {
        assertThat(new StockPackaging(12, 0, List.of(17)).total()).isEqualTo(17);
        assertThatThrownBy(() -> new StockPackaging(12, -1, List.of())).hasMessage("PACKAGING_INVALID");
        assertThatThrownBy(() -> new StockPackaging(12, 0, List.of(0))).hasMessage("PACKAGING_INVALID");
        assertThatThrownBy(() -> new StockPackaging(12, Integer.MAX_VALUE, List.of())).hasMessage("PACKAGING_INVALID");
    }
}

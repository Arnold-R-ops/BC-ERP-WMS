import type { Transfer } from '../../api/transfers';

export interface TransferAvailableActions {
  approve: boolean;
  cancel: boolean;
  closeRemainder: boolean;
  closeShortage: boolean;
  edit: boolean;
  reallocate: boolean;
  reject: boolean;
  submit: boolean;
  void: boolean;
}

export function getTransferAvailableActions(transfer: Transfer): TransferAvailableActions {
  const dispatched = transfer.lines.reduce((sum, line) => sum + (line.dispatchedQuantity ?? 0), 0);
  const allocated = transfer.lines.reduce((sum, line) => sum + (line.allocatedQuantity ?? 0), 0);
  const inTransit = transfer.lines.reduce((sum, line) => sum + (line.inTransitQuantity ?? 0), 0);
  const editable = (transfer.businessStatus === 'DRAFT' || transfer.businessStatus === 'REJECTED')
    && dispatched === 0;

  return {
    approve: transfer.businessStatus === 'PENDING_APPROVAL',
    cancel: ['DRAFT', 'REJECTED', 'PENDING_APPROVAL', 'APPROVED'].includes(transfer.businessStatus)
      && dispatched === 0,
    closeRemainder: transfer.businessStatus === 'APPROVED' && transfer.outboundStatus === 'PARTIAL',
    closeShortage: transfer.businessStatus === 'APPROVED' && inTransit > 0,
    edit: editable,
    reallocate: transfer.businessStatus === 'APPROVED',
    reject: transfer.businessStatus === 'PENDING_APPROVAL',
    submit: transfer.businessStatus === 'DRAFT' || transfer.businessStatus === 'REJECTED',
    void: editable && allocated === 0,
  };
}

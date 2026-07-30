import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createLocation,
  listLocations,
  setLocationEnabled,
  setWarehouseActive,
  updateLocation,
  updateWarehouse,
} from './warehouseSetup';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    status: 200,
  });
}

describe('warehouse setup API contracts', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => Promise.resolve(jsonResponse({})));
  });

  it('updates the complete warehouse contact contract', async () => {
    await updateWarehouse(15, { name: 'Guangzhou', contact: 'Arnold', phone: '13800138000' });

    expect(fetch).toHaveBeenCalledWith('/api/warehouses/15', expect.objectContaining({ method: 'PUT' }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual({
      name: 'Guangzhou', contact: 'Arnold', phone: '13800138000',
    });
  });

  it('keeps warehouse lifecycle commands explicit', async () => {
    await setWarehouseActive(15, false);
    expect(fetch).toHaveBeenCalledWith('/api/warehouses/15/deactivate', expect.objectContaining({ method: 'PUT' }));
  });

  it('persists routing coordinates when creating and editing a location', async () => {
    await createLocation({
      warehouseId: 15, zone: 'ZONE_A', shelfNumber: 'A', positionNumber: '01', posX: 10, posY: 6,
    });
    await updateLocation(13, { posX: 12, posY: 8, remark: 'near receiving dock' });

    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[0]?.[1]?.body))).toEqual(expect.objectContaining({ posX: 10, posY: 6 }));
    expect(JSON.parse(String(vi.mocked(fetch).mock.calls[1]?.[1]?.body))).toEqual({ posX: 12, posY: 8, remark: 'near receiving dock' });
  });

  it('uses warehouse-scoped location queries and lifecycle commands', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse([]));
    await listLocations(15);
    await setLocationEnabled(13, false);

    expect(fetch).toHaveBeenNthCalledWith(1, '/api/locations/warehouse/15', expect.any(Object));
    expect(fetch).toHaveBeenNthCalledWith(2, '/api/locations/13/disable', expect.objectContaining({ method: 'PUT' }));
  });
});

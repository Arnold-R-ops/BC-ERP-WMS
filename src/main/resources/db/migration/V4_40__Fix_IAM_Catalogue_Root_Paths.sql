-- V4.40: AntPathMatcher does not treat /api/{catalogue}/** as the collection root.
-- Keep role workflow paths on system:role:update and make the read-only catalogue
-- permissions match their actual collection endpoints exactly.

UPDATE sys_permission
SET resource_path = '/api/roles',
    http_method = 'GET',
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'system:role:view';

UPDATE sys_permission
SET resource_path = '/api/permissions',
    http_method = 'GET',
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'system:permission:view';

package com.wms.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu Tree DTO
 *
 * Hierarchical menu structure for frontend rendering.
 * Builds tree from MENU type permissions.
 *
 * Response Format:
 * <pre>
 * {
 *   "id": 1,
 *   "permissionCode": "menu:inventory",
 *   "menuName": "库存管理",
 *   "menuUrl": "/inventory",
 *   "menuIcon": "icon-inventory",
 *   "sortOrder": 1,
 *   "children": [
 *     {
 *       "id": 2,
 *       "permissionCode": "menu:inventory:list",
 *       "menuName": "库存列表",
 *       "menuUrl": "/inventory/list",
 *       ...
 *     }
 *   ]
 * }
 * </pre>
 *
 * @author WMS Team
 * @since 2026-01-18
 * @version 2.0 (Dynamic RBAC System)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuTreeDTO {

    /**
     * Permission ID
     */
    private Long id;

    /**
     * Permission Code
     */
    private String permissionCode;

    /**
     * Menu Name (display name)
     */
    private String menuName;

    /**
     * Menu URL (frontend route)
     */
    private String menuUrl;

    /**
     * Menu Icon
     */
    private String menuIcon;

    /**
     * Parent ID
     */
    private Long parentId;

    /**
     * Sort Order
     */
    private Integer sortOrder;

    /**
     * Child Menus (recursive structure)
     */
    @Builder.Default
    private List<MenuTreeDTO> children = new ArrayList<>();

    /**
     * Check if this is a leaf node (no children)
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /**
     * Check if this is a root node (no parent)
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * Add child menu
     */
    public void addChild(MenuTreeDTO child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }
}

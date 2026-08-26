package com.nexusbattles.ms_identidad.rbac.dto;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.PermissionType;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionMatrixResponse {
    private String version;
    private Map<Role, Map<Action, PermissionType>> matrix;
}

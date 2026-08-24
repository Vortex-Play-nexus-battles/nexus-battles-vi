package com.nexusbattles.ms_identidad.rbac.dto;

import com.nexusbattles.ms_identidad.rbac.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleDescriptor {
    private Role role;
    private String name;
    private String description;
}

package br.com.bardozico;

import java.util.Set;
import java.util.UUID;

public record Actor(UUID id, UUID unit, UUID session, String name, String email, String username, Set<String> permissions, boolean attendanceOnly) {
    public Actor(UUID id,UUID unit,UUID session,String name,String email,String username,Set<String> permissions) {
        this(id,unit,session,name,email,username,permissions,false);
    }
    public void require(String permission) {
        if (!permissions.contains(permission)) throw new ApiError(403,"Você não tem permissão para esta operação.");
    }
}

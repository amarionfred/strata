package dev.omalauncher.companion;
interface IAdminService {
    String inspect() = 0;
    String setWifi(boolean enabled) = 1;
    String setRotationLocked(boolean locked) = 2;
    void destroy() = 16777114;
}


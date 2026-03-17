package com.projectmaidgroup.platform.shizuku_service;

interface IShizukuService {
    void destroy() = 16777114; // Required for UserService
    String runCommand(String cmd) = 1;
}

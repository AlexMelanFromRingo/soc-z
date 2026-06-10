cmake_minimum_required(VERSION 3.22.1)
project(socz CXX)
set(CMAKE_CXX_STANDARD 17)

add_library(socz SHARED vk_probe.cpp)
find_library(log-lib log)
find_library(vulkan-lib vulkan)
target_link_libraries(socz ${log-lib} ${vulkan-lib})

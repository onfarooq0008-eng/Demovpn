cmake_minimum_required(VERSION 3.22.1)
project(fastvpn_ovpn3 CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../..)
set(OVPN3 ${ROOT}/third_party/openvpn3)
set(ASIO  ${ROOT}/third_party/asio/asio)

find_package(openssl REQUIRED CONFIG)   # Prefab from com.android.ndk.thirdparty:openssl

add_library(fastvpn_ovpn3 SHARED jni_client.cpp ${OVPN3}/client/ovpncli.cpp)
target_include_directories(fastvpn_ovpn3 PRIVATE ${OVPN3} ${OVPN3}/client ${ASIO}/include)
target_compile_definitions(fastvpn_ovpn3 PRIVATE
    USE_ASIO ASIO_STANDALONE ASIO_NO_DEPRECATED ASIO_HAS_STD_ADDRESSOF ASIO_HAS_STD_ARRAY
    ASIO_HAS_CSTDINT ASIO_HAS_STD_SHARED_PTR ASIO_HAS_STD_TYPE_TRAITS
    USE_OPENSSL USE_TUN_BUILDER)
target_compile_options(fastvpn_ovpn3 PRIVATE -fexceptions -frtti -Wno-deprecated-declarations -Wno-unused-parameter)
target_link_libraries(fastvpn_ovpn3 openssl::ssl openssl::crypto log)

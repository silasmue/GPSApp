// IGPSService.aidl
package com.example.gps;

// Declare any non-default types here with import statements

interface IGPSService {

    void stop();

    float getLatitude();

    float getLongitude();

    float getAltitude();

}
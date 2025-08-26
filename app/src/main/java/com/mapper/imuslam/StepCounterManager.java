package com.mapper.imuslam;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicInteger;

public class StepCounterManager implements SensorEventListener {

    private final SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private int initialStepCount = -1;
    private final AtomicInteger sessionSteps = new AtomicInteger(0); // Make final to prevent reassignment
    private static final float AVERAGE_STEP_LENGTH = Settings.getInstance().getConstant();

    private static StepCounterManager instance;

    private StepCounterManager(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            if (stepCounterSensor == null) {
                Toast.makeText(context, "Step counter sensor not available!", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(context, "Sensor service not available!", Toast.LENGTH_SHORT).show();
        }
    }

    public static synchronized StepCounterManager getInstance(Context context) {
        if (instance == null) {
            instance = new StepCounterManager(context.getApplicationContext());
        }
        return instance;
    }

    public void register() {
        if (sensorManager != null && stepCounterSensor != null) {
//            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_FASTEST);
            Log.d("StepCounterManager", "Registered step counter sensor");
        }
    }

    public void unregister() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            Log.d("StepCounterManager", "Unregistered sensor listener");
        }
    }

    public int getSessionSteps() {
        return sessionSteps.get();
    }

    public float getDistanceMeters() {
        return sessionSteps.get() * AVERAGE_STEP_LENGTH;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            int totalSteps = (int) event.values[0];
            if (initialStepCount < 0) {
                initialStepCount = totalSteps;
            }
            // Update the AtomicInteger value correctly
            sessionSteps.set(totalSteps - initialStepCount);
            Log.d("StepCounterManager", "totalSteps: "+String.valueOf(totalSteps)+" initialStepCount: "+String.valueOf(initialStepCount));
            Log.d("StepCounterManager", " Steps: " + sessionSteps.get());
        }
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            Log.d("StepCounterManager",event.values.toString());
        }
        if(event.sensor.getType()==Sensor.TYPE_GYROSCOPE){
            Log.d("StepCounterManager",event.values.toString());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No action needed
        Log.d("StepCounterManager","sensor: "+sensor.toString()+"accuracy: "+String.valueOf(accuracy));
    }

    public void resetSessionSteps() {
        sessionSteps.set(0); // Correct way to reset AtomicInteger
        initialStepCount = -1;
        Log.d("StepCounterManager", "Reset session steps");
    }
}

//public class StepCounterManager implements SensorEventListener {
//
//    private final SensorManager sensorManager;
//    private Sensor stepCounterSensor;
//    private int initialStepCount = -1;
//    private AtomicInteger sessionSteps = new AtomicInteger(0);
//    private static final float AVERAGE_STEP_LENGTH = Settings.getInstance().getConstant();
//
//    private static StepCounterManager instance;
//
//    private StepCounterManager(Context context) {
//        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
//        if (sensorManager != null) {
//            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
//        }else{
//            Toast.makeText(context, "STEP COUNT INITIALISATION FAILED", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    public static StepCounterManager getInstance(Context context) {
//        if (instance == null) {
//            instance = new StepCounterManager(context);
//        }
//        return instance;
//    }
//
//    public void register() {
//        if (stepCounterSensor != null) {
//            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_UI);
//            Log.d("StepCounterManager", "Registered step counter sensor");
//        }
//    }
//
//    public void unregister() {
//        sensorManager.unregisterListener(this);
//    }
//
//    public int getSessionSteps() {
//        return sessionSteps.get();
//    }
//
//    public float getDistanceMeters() {
//        return sessionSteps.get() * AVERAGE_STEP_LENGTH;
//    }
//
//    @Override
//    public void onSensorChanged(SensorEvent event) {
//        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
//            int totalSteps = (int) event.values[0];
//            if (initialStepCount < 0) {
//                initialStepCount = totalSteps;
//            }
//            sessionSteps = totalSteps - initialStepCount;
//            Log.d("StepCounterManager", "Steps: " + sessionSteps);
//        }
//    }
//
//    @Override
//    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        // No action needed.
//    }
//    public void resetSessionSteps() {
//        sessionSteps = 0;
//        initialStepCount = -1; // Reset to detect a new step count base
//    }
//}

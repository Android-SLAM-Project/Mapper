package com.mapper.imuslam;

public class Settings {
    private static Settings instance = null;

    // A flag that will be shared across activities
    private boolean trailing_flag;
    // A constant value; default is set to 7.5
    private float constant;

    // Private constructor to prevent instantiation
    private Settings() {
        // Initialize the flag to true by default and constant to 7.5
        trailing_flag = true;
        constant = 0.75F;
    }

    // Method to get the single instance of the class
    public static synchronized Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }

    // Getter and setter for the flag
    public boolean getTrailingFlag() {
        return trailing_flag;
    }

    public void setTrailing_flag(boolean value) {
        trailing_flag = value;
    }

    // Getter and setter for the constant
    public float getConstant() {
        return constant;
    }

    public void setConstant(float value) {
        constant = value;
    }
}

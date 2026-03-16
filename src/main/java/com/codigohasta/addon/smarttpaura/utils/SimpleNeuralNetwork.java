package com.codigohasta.addon.smarttpaura.utils;

public class SimpleNeuralNetwork {
    private double[][] weights;
    
    public SimpleNeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        // 初始化权重
        weights = new double[hiddenSize][inputSize + 1]; // +1 for bias
        
        // 随机初始化权重
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j <= inputSize; j++) {
                weights[i][j] = Math.random() * 2 - 1; // -1 to 1
            }
        }
    }
    
    public double[] predict(double[] inputs) {
        int hiddenSize = weights.length;
        double[] outputs = new double[hiddenSize];
        
        for (int i = 0; i < hiddenSize; i++) {
            double sum = weights[i][weights[i].length - 1]; // bias
            
            for (int j = 0; j < inputs.length; j++) {
                sum += weights[i][j] * inputs[j];
            }
            
            outputs[i] = sigmoid(sum);
        }
        
        return outputs;
    }
    
    public void train(double[] inputs, double[] targets, double learningRate) {
        // 简单训练（单层感知器）
        double[] outputs = predict(inputs);
        
        for (int i = 0; i < weights.length; i++) {
            double error = targets[0] - outputs[i];
            
            // 更新权重
            for (int j = 0; j < inputs.length; j++) {
                weights[i][j] += learningRate * error * inputs[j];
            }
            
            // 更新偏置
            weights[i][weights[i].length - 1] += learningRate * error;
        }
    }
    
    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
    
    public double[][] getWeights() { return weights; }
    public void setWeights(double[][] weights) { this.weights = weights; }
}
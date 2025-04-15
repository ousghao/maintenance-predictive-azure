package com.example.predictive_maintenance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/predict")
public class PredictionController {

    @Autowired
    private ModelService modelService;

    public static class PredictionRequest {
        public double airTemperature;
        public double processTemperature;
        public double rotationalSpeed;
        public double torque;
        public double toolWear;
        public int typeEncoded;
    }

    public static class PredictionResponse {
        public String prediction;

        public PredictionResponse(String prediction) {
            this.prediction = prediction;
        }
    }

    @PostMapping
    public PredictionResponse predict(@RequestBody PredictionRequest request) {
        String result = modelService.predict(
                request.airTemperature,
                request.processTemperature,
                request.rotationalSpeed,
                request.torque,
                request.toolWear,
                request.typeEncoded
        );
        return new PredictionResponse(result);
    }
}

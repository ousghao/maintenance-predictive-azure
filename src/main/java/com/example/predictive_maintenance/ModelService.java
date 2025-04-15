package com.example.predictive_maintenance;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import smile.classification.LogisticRegression;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.validation.metric.Accuracy;

import javax.annotation.PostConstruct;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

@Service
public class ModelService {

    private LogisticRegression model;

    @PostConstruct
    public void init() {
        try {
            // Chargement du fichier CSV
            Reader in = new InputStreamReader(getClass().getResourceAsStream("/ai4i2020.csv"));
            Iterable<CSVRecord> records = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(in);

            // Listes pour stocker les données
            List<Double> airTemps = new ArrayList<>();
            List<Double> processTemps = new ArrayList<>();
            List<Double> speeds = new ArrayList<>();
            List<Double> torques = new ArrayList<>();
            List<Double> toolWears = new ArrayList<>();
            List<Integer> typesEncoded = new ArrayList<>();
            List<Integer> targets = new ArrayList<>();

            // Encodage du champ "Type"
            Map<String, Integer> typeEncoding = new HashMap<>();
            int typeCounter = 0;

            for (CSVRecord record : records) {
                String type = record.get("Type");
                if (!typeEncoding.containsKey(type)) {
                    typeEncoding.put(type, typeCounter++);
                }

                typesEncoded.add(typeEncoding.get(type));
                airTemps.add(Double.parseDouble(record.get("Air temperature [K]")));
                processTemps.add(Double.parseDouble(record.get("Process temperature [K]")));
                speeds.add(Double.parseDouble(record.get("Rotational speed [rpm]")));
                torques.add(Double.parseDouble(record.get("Torque [Nm]")));
                toolWears.add(Double.parseDouble(record.get("Tool wear [min]")));
                targets.add(Integer.parseInt(record.get("Machine failure")));
            }

            // Analyse du balancement initial
            long failureCount = targets.stream().filter(i -> i == 1).count();
            long normalCount = targets.stream().filter(i -> i == 0).count();

            System.out.println("🔍 Analyse initiale du balancement des données :");
            System.out.println("Failures (1) : " + failureCount);
            System.out.println("Normal (0) : " + normalCount);

            // Conversion des listes en arrays
            double[] airTempArray = airTemps.stream().mapToDouble(Double::doubleValue).toArray();
            double[] processTempArray = processTemps.stream().mapToDouble(Double::doubleValue).toArray();
            double[] speedArray = speeds.stream().mapToDouble(Double::doubleValue).toArray();
            double[] torqueArray = torques.stream().mapToDouble(Double::doubleValue).toArray();
            double[] toolWearArray = toolWears.stream().mapToDouble(Double::doubleValue).toArray();
            int[] typeArray = typesEncoded.stream().mapToInt(Integer::intValue).toArray();
            int[] targetArray = targets.stream().mapToInt(Integer::intValue).toArray();

            // Construction du DataFrame initial
            DataFrame data = DataFrame.of(
                    IntVector.of("Type", typeArray),
                    DoubleVector.of("Air temperature [K]", airTempArray),
                    DoubleVector.of("Process temperature [K]", processTempArray),
                    DoubleVector.of("Rotational speed [rpm]", speedArray),
                    DoubleVector.of("Torque [Nm]", torqueArray),
                    DoubleVector.of("Tool wear [min]", toolWearArray),
                    IntVector.of("Target", targetArray)
            );

            // Séparer les classes majoritaire et minoritaire
            List<Tuple> majoritySamples = new ArrayList<>();
            List<Tuple> minoritySamples = new ArrayList<>();

            for (int i = 0; i < data.nrows(); i++) {
                Tuple row = data.get(i);
                if (row.getInt("Target") == 1) {
                    minoritySamples.add(row);
                } else {
                    majoritySamples.add(row);
                }
            }

            // Sur-échantillonnage complet jusqu'à l'équilibre parfait
            List<Tuple> balancedData = new ArrayList<>(majoritySamples);
            while (balancedData.stream().filter(t -> t.getInt("Target") == 1).count()
                    < balancedData.stream().filter(t -> t.getInt("Target") == 0).count()) {
                balancedData.addAll(minoritySamples);
            }

            System.out.println("🔄 Dataset équilibré :");
            long finalFailure = balancedData.stream().filter(t -> t.getInt("Target") == 1).count();
            long finalNormal = balancedData.stream().filter(t -> t.getInt("Target") == 0).count();
            System.out.println("Failures (1) : " + finalFailure);
            System.out.println("Normal (0) : " + finalNormal);

            // Mélanger les données équilibrées
            Collections.shuffle(balancedData);
            data = DataFrame.of(balancedData);

            // Séparer entraînement / test
            int totalRows = (int) data.nrows();
            int trainSize = (int) (totalRows * 0.8);

            DataFrame trainData = data.slice(0, trainSize);
            DataFrame testData = data.slice(trainSize, totalRows);

            double[][] trainFeatures = trainData.drop("Target").toArray();
            int[] trainLabels = trainData.intVector("Target").toIntArray();

            double[][] testFeatures = testData.drop("Target").toArray();
            int[] testLabels = testData.intVector("Target").toIntArray();

            // Entraînement du modèle
            model = LogisticRegression.fit(trainFeatures, trainLabels);

            // Évaluation du modèle
            int[] predictions = model.predict(testFeatures);
            double accuracy = Accuracy.of(testLabels, predictions);

            System.out.println("✅ Modèle équilibré et entraîné avec succès !");
            System.out.printf("🎯 Précision du modèle : %.2f%%\n", accuracy * 100);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String predict(double airTemp, double processTemp, double speed, double torque, double toolWear, int typeEncoded) {
        if (model == null) {
            return "Modèle non entraîné.";
        }

        double[] features = {typeEncoded, airTemp, processTemp, speed, torque, toolWear};
        int prediction = model.predict(features);
        return prediction == 1 ? "Défaillance" : "Normal";
    }
}

package org.example.demo;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.commons.text.StringEscapeUtils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class YouBikeApp extends Application {

//    private TextArea textArea;
    private TextField searchField;
    private ListView<String> dataList; // 使用 ListView 替代 JList

    private ListView<String> saveList;
    private List<JSONObject> bikeData;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> savedDataCollection;
    private Button reloadButton;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("YouBike App");

//        textArea = new TextArea();
//        textArea.setEditable(false);

        searchField = new TextField();
        Button searchButton = new Button("搜尋");
        searchButton.setOnAction(event -> {
            try {
                searchBikeData();
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        });

        reloadButton = new Button("重新載入");
        reloadButton.setOnAction(event -> {

            dataList.getItems().clear();
            saveList.getItems().clear();


            loadYouBikeData();
        });

        dataList = new ListView<>(); // 使用 ListView 替代 JList
        dataList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        saveList = new ListView<>();
        saveList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Button saveButton = new Button("儲存");
        saveButton.setOnAction(event -> saveSelectedData(saveList));

        Button deleteButton = new Button("刪除");
        deleteButton.setOnAction(event -> {
            String selectedItem = saveList.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {

                deleteSelectedData(selectedItem);
            }
        });

        VBox root = new VBox(10);
        root.getChildren().addAll(searchField, searchButton, dataList, new HBox(10, saveButton, deleteButton), saveList, reloadButton);

        Scene scene = new Scene(root, 600, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // 連接到 MongoDB
        mongoClient = MongoClients.create("mongodb+srv://hannn:a75LATa2da3U4OW4@clusterjava.vsekiwi.mongodb.net/?retryWrites=true&w=majority");
        database = mongoClient.getDatabase("your-database-name");
        savedDataCollection = database.getCollection("savedData");


        loadYouBikeData();
    }

    @Override
    public void stop() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    private void loadYouBikeData() {
        try {
            URL url = new URL("https://tcgbusfs.blob.core.windows.net/dotapp/youbike/v2/youbike_immediate.json");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(new String(line.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            }

            reader.close();
            connection.disconnect();


            String escapedJsonData = StringEscapeUtils.unescapeJava(response.toString());

            parseYouBikeData(escapedJsonData);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // 從 MongoDB 中加載數據並檢查它是否存在於 JSON 數據中
        savedDataCollection.find().forEach(document -> {
            String stationName = document.getString("stationName");
            Optional<JSONObject> matchingBikeObject = bikeData.stream()
                    .filter(bikeObject -> bikeObject.getString("sna").equals(stationName))
                    .findFirst();

            matchingBikeObject.ifPresent(bikeObject -> {
                String area = bikeObject.getString("sarea");
                int sbi = bikeObject.getInt("sbi");
                int bemp = bikeObject.getInt("bemp");
                String displayText = "地區: " + area +
                        ", 站名: " + stationName +
                        ", 可借車輛: " + sbi +
                        ", 可停空位: " + bemp;
                saveList.getItems().add(displayText);
            });
        });
    }

    private void parseYouBikeData(String jsonData) throws UnsupportedEncodingException {
        bikeData = new ArrayList<>();

        JSONArray jsonArray = new JSONArray(jsonData);

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject bikeObject = jsonArray.getJSONObject(i);
            bikeData.add(bikeObject);
        }

        displayBikeData();
    }

    private void displayBikeData() throws UnsupportedEncodingException {
        ObservableList<String> displayList = FXCollections.observableArrayList();

        for (JSONObject bikeObject : bikeData) {
            String area = bikeObject.getString("sarea");
            String sna = URLDecoder.decode(bikeObject.getString("sna"), "UTF-8");
            int sbi = bikeObject.getInt("sbi");
            int bemp = bikeObject.getInt("bemp");

            displayList.add("地區: " + area +
                    ", 站名: " + sna +
                    ", 可借車輛: " + sbi +
                    ", 可停空位: " + bemp);
        }

        dataList.setItems(displayList);
    }

    private void searchBikeData() throws UnsupportedEncodingException {
        String searchTerm = searchField.getText().trim().toLowerCase();

        if (searchTerm.isEmpty()) {
            displayBikeData();
        } else {
            ObservableList<String> displayList = FXCollections.observableArrayList();

            for (JSONObject bikeObject : bikeData) {
                if (bikeObject.getString("sarea").toLowerCase().contains(searchTerm) ||
                        bikeObject.getString("sna").toLowerCase().contains(searchTerm)) {
                    displayList.add("地區: " + bikeObject.getString("sarea") +
                            ", 站名: " + bikeObject.getString("sna") +
                            ", 可借車輛: " + bikeObject.getInt("sbi") +
                            ", 可停空位: " + bikeObject.getInt("bemp"));
                }
            }

            dataList.setItems(displayList);
        }
    }

    private void saveSelectedData(ListView<String> savedList) {
        ObservableList<String> selectedItems = dataList.getSelectionModel().getSelectedItems();
        List<String> uniqueStationNames = new ArrayList<>();

        for (String selectedItem : selectedItems) {
            String stationName = parseStationName(selectedItem);

            if (!isStationNameExists(stationName)) {
                Document document = new Document("stationName", stationName);
                savedDataCollection.insertOne(document);
                uniqueStationNames.add(selectedItem);
            }
        }

        if (!uniqueStationNames.isEmpty()) {
            StringBuilder selectedData = new StringBuilder();
            for (String selectedItem : uniqueStationNames) {
                selectedData.append(selectedItem).append("\n");
            }

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("儲存資訊");
            alert.setHeaderText(null);
            alert.setContentText("已儲存該資訊到 MongoDB。");
            alert.showAndWait();

            saveList.getItems().addAll(uniqueStationNames);
        }
    }

    private boolean isStationNameExists(String stationName) {
        // 檢查 MongoDB 中是否已存在相同站名的文檔
        return savedDataCollection.find(new Document("stationName", stationName)).first() != null;
    }

    private String parseStationName(String selectedItem) {
        int start = selectedItem.indexOf(", 站名:") + 6;
        int end = selectedItem.indexOf(", 可借車輛:");
        return selectedItem.substring(start, end).trim();
    }

    private void deleteSelectedData(String selectedItem) {
        String stationName = parseStationName(selectedItem);
        // 從 MongoDB 中刪除相應的數據
        savedDataCollection.deleteOne(new Document("stationName", stationName));

        saveList.getItems().remove(selectedItem);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("刪除資訊");
        alert.setHeaderText(null);
        alert.setContentText("已從 MongoDB 刪除該資訊。");
        alert.showAndWait();
    }

}

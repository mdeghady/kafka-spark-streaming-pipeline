import com.kafka.schema.avro.FakeUsers;
import com.kafka.schema.avro.GenderType;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.HttpResponse;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.UUIDSerializer;
import org.json.JSONArray;
import org.json.JSONObject;


import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;


public class FakeUsersProducer {
    public static void main(String[] args) throws Exception {


        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG , "kafka1:9092,kafka2:9093,kafka3:9094");
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG , UUIDSerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG , KafkaAvroSerializer.class);
        producerProperties.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG , "http://localhost:8081");

        KafkaProducer<UUID , FakeUsers> producer = new KafkaProducer<>(producerProperties);

        Thread shutdownhook = new Thread(producer::close);
        Runtime.getRuntime().addShutdownHook(shutdownhook);

        int messagesCounter = 0;

        while (true){
            Map newUserData = unpackResponse( getDataFromURL() );

            UUID key = (UUID)newUserData.get("id");
            FakeUsers newUser = FakeUsers.newBuilder()
                    .setId(key.toString())
                    .setAge((int)newUserData.get("age"))
                    .setEmail((String)newUserData.get("email"))
                    .setFirstName((String)newUserData.get("first_name"))
                    .setLastName((String)newUserData.get("last_name"))
                    .setCity((String)newUserData.get("city"))
                    .setCountry((String)newUserData.get("country"))
                    .setPicture((String)newUserData.get("picture"))
                    .setGender((GenderType) newUserData.get("gender"))
                    .build();

            ProducerRecord<UUID , FakeUsers> producerRecord =
                    new ProducerRecord<>("FakeUsers" , key , newUser);

            System.out.println("Sending message with key " + key + " to kafka");
            producer.send(producerRecord);
            producer.flush();
            messagesCounter ++;

            System.out.println("Successfully produced " + messagesCounter + " messages to kafka \n");

            Thread.sleep(5000);

        }





    }



    private static JSONObject getDataFromURL() throws Exception{
        HttpResponse<JsonNode> httpResponse  = Unirest.get("https://randomuser.me/api/")
                .asJson();
        JSONArray resultsArray = httpResponse
                .getBody()
                .getObject()
                .getJSONArray("results");
        JSONObject contnts = (JSONObject)resultsArray.get(0);

        return contnts;
    }

    private static Map unpackResponse(JSONObject responseContent){
        Map<String , Object> contentDictionary = new HashMap<String , Object>();

        UUID id = UUID.randomUUID();

        String responseGender = responseContent.getString("gender");
        String responseEmail = responseContent.getString("email");

//      retrieve user's name
        JSONObject name = (JSONObject)responseContent.get("name");
        String first_name = name.getString("first");
        String last_name = name.getString("last");

//       retrieve user's location
        JSONObject location = (JSONObject)responseContent.get("location");
        String city = location.getString("city");
        String country = location.getString("country");

//       retrieve user's age
        JSONObject dob = (JSONObject)responseContent.get("dob");
        int age = dob.getInt("age");

//       retrieve user's picture
        JSONObject picture = (JSONObject)responseContent.get("picture");
        String largePicture = picture.getString("large");

        contentDictionary.put("id" , id);
        contentDictionary.put("gender" , GenderType.valueOf(responseGender));
        contentDictionary.put("email" , responseEmail);
        contentDictionary.put("first_name" , first_name);
        contentDictionary.put("last_name" , last_name);
        contentDictionary.put("city" , city);
        contentDictionary.put("country" , country);
        contentDictionary.put("age" , age);
        contentDictionary.put("picture" , largePicture);
        return contentDictionary;
    }
}

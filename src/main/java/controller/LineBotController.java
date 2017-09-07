package controller;


import com.google.gson.Gson;
import com.linecorp.bot.client.LineMessagingServiceBuilder;
import com.linecorp.bot.client.LineSignatureValidator;
import com.linecorp.bot.model.Multicast;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.URIAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.CarouselColumn;
import com.linecorp.bot.model.message.template.CarouselTemplate;
import com.linecorp.bot.model.profile.UserProfileResponse;
import com.linecorp.bot.model.response.BotApiResponse;
import database.Dao;
import model.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@RestController
@RequestMapping(value = "/linebot")
public class LineBotController {
//inisialisasi channel secret

    @Autowired

    @Qualifier("com.linecorp.channel_secret")

    String lChannelSecret;

    //inisialisasi channel access token

    @Autowired

    @Qualifier("com.linecorp.channel_access_token")

    String lChannelAccessToken;

    @Autowired

    Dao mDao;


    private Payload payload;

    private Events lineEvent;

    private String replyToken;

    private String sourceType = "user";

    private String eventTextMesssage;

    private String jObjGet = "";

    private String senderUserId;

    private UserProfileResponse senderUser;


    @RequestMapping(value = "/callback", method = RequestMethod.POST)


    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String aXLineSignature,
            @RequestBody String aPayload
    ) {
// compose body
        final String text = String.format("The Signature is: %s",
                (aXLineSignature != null && aXLineSignature.length() > 0) ? aXLineSignature : "N/A");
        System.out.println(text);


        final boolean valid = new LineSignatureValidator(lChannelSecret.getBytes()).validateSignature(aPayload.getBytes(), aXLineSignature);
        System.out.println("The signature is: " + (valid ? "valid" : "tidak valid"));
//Get events from source
        if (aPayload != null && aPayload.length() > 0)
            System.out.println("Payload: " + aPayload);
        Gson gson = new Gson();
        payload = gson.fromJson(aPayload, Payload.class);

//Variable initialization
        lineEvent = payload.events[0];
        replyToken = lineEvent.replyToken;
        sourceType = lineEvent.source.type;
        senderUserId = lineEvent.source.userId;
        String eventType = lineEvent.type;

//Get event's type
        if (eventType.equals("join")) {
            TemplateMessage welcomeMessage = greetingMessage();
            this.reply(this.replyToken, welcomeMessage);
        } else if (eventType.equals("follow")) {
            TemplateMessage welcomeMessage = greetingMessage();
            this.reply(this.replyToken, welcomeMessage);
        } else if (eventType.equals("message")) {
            if (senderUserId.isEmpty() && !eventTextMesssage.toLowerCase().contains("bot leave"))
                this.replyText(replyToken, "Hi, tambahkan dulu bot Dicoding Event sebagai teman!");
            else {
                senderUser = getUserProfile(senderUserId);
                handleEventMessage();
            }
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }
//Method untuk reply message


    private void reply(String replyToken, com.linecorp.bot.model.message.Message message) {
        List<Message> messageList = new ArrayList<>();
        messageList.add(message);
        reply(replyToken, messageList);
    }


    private void reply(String replyToken, List<com.linecorp.bot.model.message.Message> messages) {
        try {
            retrofit2.Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .replyMessage(new ReplyMessage(replyToken, messages))
                    .execute();
            System.out.println("Reply Message: " + response.code() + " " + response.message());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void replyText(String replyToken, String message) {
        if (replyToken.isEmpty()) {
            throw new IllegalArgumentException("replyToken must not be empty");
        }
        if (message.length() > 1000) {
            message = message.substring(0, 1000 - 2) + "……";
        }
        this.reply(replyToken, new TextMessage(message));
    }


    private void replyText(String replyToken, String[] txtMessage) {
        List<com.linecorp.bot.model.message.Message> textMessages = new ArrayList<>();
        for (int i = 0; i < txtMessage.length; i++)
            textMessages.add(new TextMessage(txtMessage[i]));
        this.reply(replyToken, textMessages);
    }

    //method untuk mendapatkan profile user (user id, display name, image, status)


    private UserProfileResponse getUserProfile(String userId) {
        retrofit2.Response<UserProfileResponse> response = null;
        try {
            response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .getProfile(userId)
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (response.isSuccessful()) return response.body();
        else System.out.println(response.code() + " " + response.message());
        return null;
    }

    //method untuk membuat button template


    private TemplateMessage buttonTemplate(String message, String label, String action, String title) {
        ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null, title, message,
                Collections.singletonList(new MessageAction(label, action)));
        return new TemplateMessage(title, buttonsTemplate);
    }

    //method untuk mengirim pesan saat ada user menambahkan bot sebagai teman

    private TemplateMessage greetingMessage() {
        String greetingMsg = "Hi ";
        switch (sourceType) {
            case "user":
                greetingMsg += senderUser.getDisplayName().split("\\s+")[0].substring(0, 6) + "! ";
                break;
            case "group":
                greetingMsg += "Group! ";
                break;
            case "room":
                greetingMsg += "Room! ";
                break;
        }
        greetingMsg += "Ayo ikut dicoding event, aku bisa cariin km teman";
        String action = "Lihat daftar event";
        String title = "Welcome";
        return buttonTemplate(greetingMsg, action, action, title);
    }

// method untuk menangani event message

    private void handleEventMessage() {
        this.eventTextMesssage = lineEvent.message.text;
        String msgText = eventTextMesssage.toLowerCase();
// jika message selain text
        if (!lineEvent.message.type.equals("text")) {
            TemplateMessage welcomeMessage = greetingMessage();
            this.reply(this.replyToken, welcomeMessage);
        }
// jika message berupa text
        else {
            switch (sourceType) {
                case "user":
                    handleUserMessage(msgText);
                    break;
                case "group":
                    handleGroupMessage(msgText);
                    break;
                case "room":
                    handleRoomMessage(msgText);
                    break;
            }
        }
    }

    // method untuk menangani message dari user


    private void handleUserMessage(String msgText) {
        if (msgText.contains("id") || msgText.contains("find") || msgText.contains("join") || msgText.contains("teman")) {
            processText(msgText);
        } else try {
            getEventData(msgText);
        } catch (IOException e) {
            System.out.println("Exception is raised!");
            e.printStackTrace();
        }
    }

    // method untuk menangani message dari group


    private void handleGroupMessage(String msgText) {
        if (msgText.contains("bot leave")) {
            this.replyText(replyToken, "Good bye, Group!");
            leaveGR(lineEvent.source.groupId, sourceType);
            return;
        }

        if (msgText.contains("id") || msgText.contains("find") || msgText.contains("join") || msgText.contains("teman")) {
            processText(msgText);
        } else try {
            getEventData(msgText);
        } catch (IOException e) {
            System.out.println("Exception is raised!");
            e.printStackTrace();
        }
    }

    // method untuk menangani message dari room


    private void handleRoomMessage(String msgText) {
        if (msgText.contains("bot leave")) {
            this.replyText(replyToken, "Good bye, Room!");
            leaveGR(lineEvent.source.roomId, sourceType);
            return;
        }


        if (msgText.contains("id") || msgText.contains("find") || msgText.contains("join") || msgText.contains("teman")) {
            processText(msgText);
        } else try {
            getEventData(msgText);
        } catch (IOException e) {
            System.out.println("Exception is raised!");
            e.printStackTrace();
        }
    }

    //Method for leave group or room


    private void leaveGR(String id, String type) {
        try {
            if (type.equals("group")) {
                retrofit2.Response<BotApiResponse> response = LineMessagingServiceBuilder
                        .create(lChannelAccessToken)
                        .build()
                        .leaveGroup(id)
                        .execute();
                System.out.println(response.code() + " " + response.message());
            } else if (type.equals("room")) {
                retrofit2.Response<BotApiResponse> response = LineMessagingServiceBuilder
                        .create(lChannelAccessToken)
                        .build()
                        .leaveRoom(id)
                        .execute();
                System.out.println(response.code() + " " + response.message());
            }
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }

    //method yang berisi keyword dan trigger yang berhubungan dengan database


    private void processText(String aText)


    {
        System.out.println("message text: " + aText + " from: " + senderUserId);
        if (senderUser == null) senderUser = getUserProfile(senderUserId);

        String[] words = aText.trim().split("\\s+");
        String intent = words[0];
        System.out.println("intent: " + intent);
        String msg = " ";
        String lineId = " ";
        String eventId = " ";

        if (intent.equalsIgnoreCase("id")) {
            String target = words.length > 1 ? words[1] : "";
            if (target.length() <= 3)
                msg = "Need more than 3 character to find user";
            else {
                lineId = aText.substring(aText.indexOf("@") + 1);
                if (senderUser != null) {
                    if (!senderUser.getDisplayName().isEmpty() && (lineId.length() > 0)) {
                        String reg = regLineID(senderUserId, lineId, senderUser.getDisplayName());
                        if (!reg.equals("Yah gagal mendaftar :(")) carouselTemplateMessage();
                        return;
                    } else {

                        this.replyText(replyToken, "User tidak terdeteksi. Tambahkan dulu bot Dicoding Event sebagai teman!");
                    }
                } else {

                    this.replyText(replyToken, "User tidak terdeteksi. Tambahkan dulu bot Dicoding Event sebagai teman!");
                }
            }
        } else if (intent.equalsIgnoreCase("join")) {
            eventId = aText.substring(aText.indexOf("#") + 1);
            lineId = findUser(senderUserId);
            joinEvent(eventId, senderUserId, lineId, senderUser.getDisplayName());
            return;
        } else if (intent.equalsIgnoreCase("teman")) {
            eventId = aText.substring(aText.indexOf("#") + 1);
            String txtMessage = findEvent(eventId);
            this.replyText(replyToken, txtMessage);
            return;
        }

// if msg is invalid
        if (msg == " ") replyText(replyToken, "Message invalid");
    }
    //method mendaftarkan LINE ID


    private String regLineID(String aUserId, String aLineId, String aDisplayName) {
        String regStatus;
        String exist = findUser(aUserId);
        if (exist == "User not found") {
            int reg = mDao.registerLineId(aUserId, aLineId, aDisplayName);
            if (reg == 1) regStatus = "Yay berhasil mendaftar!";
            else regStatus = "Yah gagal mendaftar :(";
        } else regStatus = "Anda sudah terdaftar";
        return regStatus;
    }

    //method untuk bergabung dalam event


    private void joinEvent(String eventID, String aUserId, String lineID, String aDisplayName) {
        String joinStatus;
        String exist = findFriend(eventID, aUserId);

        if (Objects.equals(exist, "Event not found")) {
            int join = mDao.joinEvent(eventID, aUserId, lineID, aDisplayName);
            if (join == 1) {
                joinStatus = "Pendaftaran event berhasil! Berikut teman yang menemani kamu";
                this.reply(replyToken, buttonTemplate(joinStatus, "Lihat Teman", "teman #" + eventID, "List Teman"));
                multicastMsg(eventID, aUserId);
            } else this.replyText(replyToken, "yah gagal bergabung :(");
        } else

            this.reply(replyToken, buttonTemplate("Kamu sudah bergabung di event ini", "Lihat Teman", "teman #" + eventID, "List Teman"));

    }

    //method untuk mengirimkan pesan ke semua teman


    private void multicastMsg(String eventID, String userID) {
        List<String> listId = new ArrayList<>();
        List<JointEvents> self = mDao.getByEventId("%" + eventID + "%");
        if (self.size() > 0) {
            for (int i = 0; i < self.size(); i++) {
                listId.add(self.get(i).user_id);
                listId.remove(userID);
            }
        } else return;
        System.out.println(listId);
        String msg = "Hi, ada teman baru telah bergabung di event " + eventID;
        Set<String> stringSet = new HashSet<String>(listId);
        ButtonsTemplate buttonsTemplate = new ButtonsTemplate(null, null, msg,
        Collections.singletonList(new MessageAction("Lihat Teman", "teman #" + eventID)));
        TemplateMessage templateMessage = new TemplateMessage("List Teman", buttonsTemplate);
        Multicast multicast = new Multicast(stringSet, templateMessage);
        try {
            retrofit2.Response<BotApiResponse> response = LineMessagingServiceBuilder
                    .create(lChannelAccessToken)
                    .build()
                    .multicast(multicast)
                    .execute();
            System.out.println(response.code() + " " + response.message());
        } catch (IOException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        }
    }
//method untuk mencari user terdaftar di database


    private String findUser(String aUserId) {
        String txt = "";
        List<User> self = mDao.getByUserId("%" + aUserId + "%");
        if (self.size() > 0) {
            for (int i = 0; i < self.size(); i++) {
                User user = self.get(i);
                txt = user.line_id;
            }
        } else txt = "User not found";
        return txt;
    }

    //method untuk mencari data di table event berdasarkan event id


    private String findEvent(String eventID) {
        String txt = "Daftar teman di event " + eventID + " :";
        List<JointEvents> self = mDao.getByEventId("%" + eventID + "%");
        if (self.size() > 0) {
            for (int i = 0; i < self.size(); i++) {
                JointEvents jointEvents = self.get(i);
                txt = txt + "\n\n";
                txt = txt + getEventString(jointEvents);
            }
        } else txt = "Event not found";
        return txt;
    }
//method untuk melihat teman terdaftar di dalam suatu event

    private String findFriend(String eventID, String userID) {
        String txt = "Daftar teman di event " + eventID + " :";
        List<JointEvents> self = mDao.getByJoin(eventID, userID);
        if (self.size() > 0) {
            for (int i = 0; i < self.size(); i++) {
                JointEvents jointEvents = self.get(i);
                txt = txt + "\n\n";
                txt = txt + getEventString(jointEvents);
            }
        } else txt = "Event not found";
        return txt;
    }

    private String getEventString(JointEvents joinEvent)


    {
        return String.format("Display Name: %s\nLINE ID: %s\n", joinEvent.display_name, "http://line.me/ti/p/~" + joinEvent.line_id);
    }

    //method untuk memanggil dicoding event open api


    private void getEventData(String userTxt) throws IOException {

// Act as client with GET method
        String URI = "https://www.dicoding.com/public/api/events?limit=5&active=-1";
        System.out.println("URI: " + URI);
        CloseableHttpAsyncClient c = HttpAsyncClients.createDefault();
        try {
            c.start();
//Use HTTP Get to retrieve data
            HttpGet get = new HttpGet(URI);

            Future<HttpResponse> future = c.execute(get, null);
            HttpResponse responseGet = (HttpResponse) future.get();
            System.out.println("HTTP executed");
            System.out.println("HTTP Status of response: " + responseGet.getStatusLine().getStatusCode());

// Get the response from the GET request
            BufferedReader brd = new BufferedReader(new InputStreamReader(responseGet.getEntity().getContent()));

            StringBuffer resultGet = new StringBuffer();
            String lineGet = "";
            while ((lineGet = brd.readLine()) != null) {
                resultGet.append(lineGet);
            }
            System.out.println("Got result");

// Change type of resultGet to JSONObject
            jObjGet = resultGet.toString();
            System.out.println("Event responses: " + jObjGet);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("Exception is raised ");
            e.printStackTrace();
        } finally {
            c.close();
        }

        Gson mGson = new Gson();
        Event dicodingEvent = mGson.fromJson(jObjGet, Event.class);
        if (userTxt.equals("lihat daftar event")) {
            String userFound = findUser(senderUserId);
            if (userFound == "User not found") {
                String[] message = {
                        "Aku akan mencarikan event aktif di dicoding! Tapi, kasih tahu dulu LINE ID kamu (pake \'id @\' ya)",
                        "Contoh: id @john"
                };
                this.replyText(replyToken, message);
            } else carouselTemplateMessage();
        } else if (userTxt.contains("summary")) {
            int eventIndex = Integer.parseInt(String.valueOf(userTxt.charAt(1))) - 1;
            Data eventData = dicodingEvent.getData().get(eventIndex);
            String[] eventSummary = {
                    eventData.getSummary(),
                    "Lokasi: " + eventData.getCity_name() + "\n" + eventData.getAddress(),
                    "Waktu: " + eventData.getBegin_time() + " s/d " + eventData.getEnd_time(),
                    "Quota: " + eventData.getQuota()
            };
            this.replyText(replyToken, eventSummary);
        } else {
            List<com.linecorp.bot.model.message.Message> messages = new ArrayList<>();

            messages.add(new TextMessage("Hi " + senderUser.getDisplayName() + ", aku belum  mengerti maksud kamu. Silahkan ikuti petunjuk ya :)"));
            messages.add(greetingMessage());
            this.reply(replyToken, messages);
        }
    }

//method untuk template message berupa carousel


    private void carouselTemplateMessage() {
        Gson mGson = new Gson();
        Event event = mGson.fromJson(jObjGet, Event.class);

        if ((event == null) || (event.getData().size() < 1)) try {
            this.getEventData("lihat daftar event");
        } catch (IOException e) {
            e.printStackTrace();
        }

        int i;
        String image, owner, name, id, link;
        CarouselColumn column;
        List<CarouselColumn> carouselColumn = new ArrayList<>();
        for (i = 0; i < event.getData().size(); i++) {
            image = event.getData().get(i).getImage_path();
            owner = event.getData().get(i).getOwner_display_name();
            name = event.getData().get(i).getName();
            id = String.valueOf(event.getData().get(i).getId());
            link = event.getData().get(i).getLink();

            column = new CarouselColumn(image, name.substring(0, (name.length() < 40) ? name.length() : 40), owner,
                    Arrays.asList(new MessageAction("Summary", "[" + String.valueOf(i + 1) + "]" + " Summary : " + name),
                            new URIAction("View Page", link),
                            new MessageAction("Join Event", "join event #" + id)));
            carouselColumn.add(column);
        }

        CarouselTemplate carouselTemplate = new CarouselTemplate(carouselColumn);
        TemplateMessage templateMessage = new TemplateMessage("Your search result", carouselTemplate);
        this.reply(replyToken, templateMessage);
    }
}
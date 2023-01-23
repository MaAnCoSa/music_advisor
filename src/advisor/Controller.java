package advisor;

// Necessary to parse JSON objects.
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

// Necessary to communicate with Spotify's API.
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Controller {

    // URLs for the different commands that require request from the API.
    private static final String NEW = "/v1/browse/new-releases";
    private static final String FEATURED = "/v1/browse/featured-playlists";
    private static final String CATEGORIES = "/v1/browse/categories";

    // IDs for the application.
    private static final String CLIENT_ID = "fa886b806b8c4668982b1b1fdc3652da";
    private static final String CLIENT_SECRET = "1b64a242a5104e5795c3883cc27d5e3c";

    // Variables and values needed for the requests.
    private static final String GRANT_TYPE = "authorization_code";
    private static final String REDIRECT_URI = "http://localhost:8080";
    private static final String RESPONSE_TYPE = "code";
    private static final HttpClient client = HttpClient.newBuilder().build();
    private static HttpRequest request;
    private static HttpResponse<String> response;
    private static String authorizationCode;
    private static String accessToken;

    // Stores the amount of pages that the current command will be able to display.
    public static int amt_pages;

    // Requests the access token using the code received for authentication.
    private static HttpResponse<String> requestAccessToken() throws IOException, InterruptedException {
        // This prepares and encodes the necessary values to be sent in the access token request.
        String idAndSecret = CLIENT_ID + ":" + CLIENT_SECRET;
        String encoded = Base64.getUrlEncoder().encodeToString(idAndSecret.getBytes());

        // The request is created with the necessary key-value pairs and headers.
        request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=" + GRANT_TYPE
                                + "&code=" + authorizationCode
                                + "&redirect_uri=" + REDIRECT_URI
                                + "&client_id=" + CLIENT_ID
                                + "&client_secret=" + CLIENT_SECRET))
                .header("Authorization", "Basic " + encoded)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .uri(URI.create(Main.access + "/api/token"))
                .build();

        // The function sends the request and returns the response.
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }


    // Receives the code and asks for an access token to authorize the user.
    public static void authorize() throws IOException, InterruptedException {
        // This prepares a countdown to wait for an initial response from the API.
        CountDownLatch latch = new CountDownLatch(1);

        // The local server is created.
        HttpServer server = HttpServer.create();
        server.bind(new InetSocketAddress(8080), 0);

        // The context will listen for a response from the API.
        server.createContext("/",
                exchange -> {
                    // The response is parsed to a string.
                    String query = exchange.getRequestURI().getQuery();

                    // The response WE send to the browser is declared.
                    String responseBody;

                    // In case the query received contains the code...
                    if (query != null && query.contains("code")) {
                        // This saves the received code.
                        authorizationCode = query.substring(5);
                        // We can lower the countdown now that we have the code.
                        latch.countDown();
                        // The response to be printed on the browser is initialized.
                        responseBody = "Got the code. Return back to your program.";

                        // We print in console that the code is received. Now to ask for the access token.
                        System.out.println("code received");
                        System.out.println("making http request for access_token...");

                        // The future response is declared. (It will contain the access token.
                        HttpResponse<String> response = null;
                        try {
                            // We ask and receive the access token.
                            response = requestAccessToken();

                            // The answer is parsed into a JSON object.
                            JsonObject jo = JsonParser.parseString(response.body()).getAsJsonObject();
                            // We get the access token.
                            accessToken = jo.get("access_token").getAsString();

                            System.out.println("Success!");

                            // We print the response on the browser.
                            exchange.sendResponseHeaders(200, responseBody.length());
                            exchange.getResponseBody().write(responseBody.getBytes());
                            exchange.getResponseBody().close();

                            // We change the status of the program to authorized.
                            Main.auth = true;
                        // In case of an exception, we print it.
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    // If the response didn't had the code...
                    } else {
                        // The response to the browser is as follows:
                        responseBody = "Authorization code not found. Try again.";

                        // We print the response on the browser.
                        exchange.sendResponseHeaders(400, responseBody.length());
                        exchange.getResponseBody().write(responseBody.getBytes());
                        exchange.getResponseBody().close();
                    }
                }
        );

        // The server starts.
        server.start();
        // We print the URL link for the user to allow Spotify to give us an initial code.
        System.out.println("use this link to request the access code:");
        System.out.println(Main.access + "/authorize"
                + "?client_id=" + CLIENT_ID
                + "&redirect_uri=" + REDIRECT_URI
                + "&response_type=" + RESPONSE_TYPE);
        System.out.println("waiting for code...");

        // The server waits for a response.
        latch.await();
        // Finally, the server stops.
        server.stop(10);
    }


    // Commands will require to send a request to the API.
    private static HttpResponse<String> sendRequest(String url) throws IOException, InterruptedException {
        // The request is prepared with the necessary query and headers.
        request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .uri(URI.create(url))
                .GET()
                .build();

        // The response from the API is returned.
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }


    // Sends a request for the top 20 new albums and displays them by page.
    public static void showNew() throws IOException, InterruptedException {
        // First, we get a response from the API and parse it to a JSON object.
        response = sendRequest(Main.resource + NEW);
        JsonObject jo = JsonParser.parseString(response.body()).getAsJsonObject();

        // The function stops if there was an error.
        if (errorMsg(jo)) return;

        // We extract the list of albums into a new JSON object.
        JsonObject albums = jo.getAsJsonObject("albums");

        // We update the amount of pages that will be displayed, depending on the number of items
        // that will be shown per page and the length of the list.
        amt_pages = (int) Math.ceil(((float) albums.getAsJsonArray("items").size() / (float) Main.entries));
        // We get the initial index for the items that will be shown in the current page.
        int init = (Main.cur_page * Main.entries) - Main.entries;
        int i = 0;
        // This loop cycle will display all items that correspond to the current page.
        for (JsonElement items : albums.getAsJsonArray("items")) {
            // If the current index corresponds to the current page...
            if (i >= init) {
                // ... we extract the NAME of the album and display it.
                JsonObject item = items.getAsJsonObject();
                System.out.println(item.get("name").getAsString());
                // Then we do the same for all artists involved on the album.
                List<String> artist = new ArrayList<>();
                for (JsonElement art : item.getAsJsonArray("artists")) {
                    artist.add(art.getAsJsonObject().get("name").getAsString());
                }
                System.out.println(artist);
                // Finally, we do the same for the URL that can take the user to the album.
                System.out.println(item.get("external_urls").getAsJsonObject().get("spotify").getAsString() + "\n");
            }
            // After each cycle, the index is incremented.
            i++;
            // If that was the last item that was supposed to be displayed, the function stops.
            if (i == init + Main.entries) { break; }
        }
        // At the very end, we print the page number.
        System.out.println("---PAGE " + Main.cur_page + " OF " + amt_pages + "---");
    }


    // Sends a request for the top 20 playlists featured and displays them by page.
    public static void showFeatured() throws IOException, InterruptedException {
        // First, we get a response from the API and parse it to a JSON object.
        response = sendRequest(Main.resource + FEATURED);
        JsonObject jo = JsonParser.parseString(response.body()).getAsJsonObject();

        // The function stops if there was an error.
        if (errorMsg(jo)) return;

        // We extract the list of featured playlists into a new JSON object.
        JsonObject featured = jo.getAsJsonObject("playlists");

        // We update the amount of pages that will be displayed, depending on the number of items
        // that will be shown per page and the length of the list.
        amt_pages = (int) Math.ceil(((float) featured.getAsJsonArray("items").size() / (float) Main.entries));
        // We get the initial index for the items that will be shown in the current page.
        int init = (Main.cur_page * Main.entries) - Main.entries;
        int i = 0;
        // This loop cycle will display all items that correspond to the current page.
        for (JsonElement items : featured.getAsJsonArray("items")) {
            // If the current index corresponds to the current page...
            if (i >= init) {
                // ...we extract the playlist...
                JsonObject item = items.getAsJsonObject();
                // ...and print its NAME and URL.
                System.out.println(item.get("name").getAsString());
                System.out.println(item.get("external_urls").getAsJsonObject().get("spotify").getAsString());
                System.out.println("");
            }
            // After each cycle, the index is incremented.
            i++;
            // If that was the last item that was supposed to be displayed, the function stops.
            if (i == init + Main.entries) { break; }
        }
        // At the very end, we print the page number.
        System.out.println("---PAGE " + Main.cur_page + " OF " + amt_pages + "---");
    }


    // Sends a request for the category names and displays them by page.
    public static void showCategories() throws IOException, InterruptedException {
        // First, we get a response from the API and parse it to a JSON object.
        response = sendRequest(Main.resource + CATEGORIES);
        JsonObject jo = JsonParser.parseString(response.body()).getAsJsonObject();

        // The function stops if there was an error.
        if (errorMsg(jo)) return;

        // We extract the list of categories into a new JSON object.
        JsonObject categories = jo.getAsJsonObject("categories");

        // We update the amount of pages that will be displayed, depending on the number of items
        // that will be shown per page and the length of the list.
        amt_pages = (int) Math.ceil(((float) categories.getAsJsonArray("items").size() / (float) Main.entries));
        // We get the initial index for the items that will be shown in the current page.
        int init = (Main.cur_page * Main.entries) - Main.entries;
        int i = 0;
        // This loop cycle will display all items that correspond to the current page.
        for (JsonElement items : categories.getAsJsonArray("items")) {
            // If the current index corresponds to the current page...
            if (i >= init) {
                // ...we extract the category and display its NAME.
                JsonObject item = items.getAsJsonObject();
                System.out.println(item.get("name").getAsString());
            }
            // After each cycle, the index is incremented.
            i++;
            // If that was the last item that was supposed to be displayed, the function stops.
            if (i == init + Main.entries) { break; }
        }
        // At the very end, we print the page number.
        System.out.println("---PAGE " + Main.cur_page + " OF " + amt_pages + "---");
    }

    // Sends a request for the top 20 playlists of a given category and displays them by page.
    public static void showPlaylists(String input) throws IOException, InterruptedException {
        // Before calling the playlists, we need to get the category ID.

        // First, we get a response from the API and parse it to a JSON object.
        response = sendRequest(Main.resource + CATEGORIES);
        JsonObject jo = JsonParser.parseString(response.body()).getAsJsonObject();

        // The function stops if there was an error.
        if (errorMsg(jo)) return;

        // We extract the list of categories into a new JSON object.
        JsonObject categories = jo.getAsJsonObject("categories");

        // In case of exception, we cover everything in a TRY-CATCH block.
        try {
            // We initialize the Category ID.
            StringBuffer id = new StringBuffer("");
            // We cycle through all categories we got,...
            for (JsonElement items : categories.getAsJsonArray("items")) {
                // ...parse it to JSON object...
                JsonObject item = items.getAsJsonObject();
                // ... and finally extract its ID if its the one we are looking for.
                if (item.get("name").getAsString().equals(input)) {
                    id.append(item.get("id").getAsString());
                    break;
                }
            }

            // Now, we get a response with the playlists and parse it to a JSON object.
            HttpResponse<String> res = sendRequest(Main.resource + CATEGORIES + "/" + id + "/playlists");
            JsonObject category = JsonParser.parseString(res.body()).getAsJsonObject();


            // The function stops if there was an error.
            if (errorMsg(category)) return;
            JsonObject playlists = category.getAsJsonObject("playlists");

            // We update the amount of pages that will be displayed, depending on the number of items
            // that will be shown per page and the length of the list.
            amt_pages = (int) Math.ceil(((float) playlists.getAsJsonArray("items").size() / (float) Main.entries));
            // We get the initial index for the items that will be shown in the current page.
            int init = (Main.cur_page * Main.entries) - Main.entries;
            int i = 0;
            // This loop cycle will display all items that correspond to the current page.
            for (JsonElement items : playlists.getAsJsonArray("items")) {
                // If the current index corresponds to the current page...
                if (i >= init) {
                    // ...we extract the playlist...
                    JsonObject item = items.getAsJsonObject();
                    // ...and print its NAME and URL.
                    System.out.println(item.get("name").getAsString());
                    System.out.println(item.get("external_urls").getAsJsonObject().get("spotify").getAsString());
                    System.out.println("");
                }
                // After each cycle, the index is incremented.
                i++;
                // If that was the last item that was supposed to be displayed, the function stops.
                if (i == init + Main.entries) { break; }
            }
            // At the very end, we print the page number.
            System.out.println("---PAGE " + Main.cur_page + " OF " + amt_pages + "---");
        // In case of an exception, we print it.
        } catch(Exception e) {
            System.out.println("Unknown category name.");
        }
    }

    // Displays an error in case of an exception in the Playlists function.
    private static boolean errorMsg(JsonObject jo) {
        if (jo.has("error")) {
            String msg = jo.getAsJsonObject("error").get("message").getAsString();
            System.out.println(msg);
            return true;
        }
        return false;
    }
}

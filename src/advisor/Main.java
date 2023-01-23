package advisor;

// Necessary in case of an exception.
import java.io.IOException;

// Necessary to handle the user's input.
import java.util.Scanner;

public class Main {

    // Necessary for authorization and for connection with the API.
    public static boolean auth = false;
    public static String access = "https://accounts.spotify.com";
    public static String resource = "https://api.spotify.com";

    public static int entries = 5;      // Num of entries per page.
    public static int cur_page = 1;     // Initializes the current page number.
    public static String last_com = ""; // Stores the last command used.
    public static String pg_com = "";   // Stores if the last command involved a page change.
    public static String last_param;    // Stores the last parameter.


    public static boolean go = true;    // If it is changed to false, it is to exit the program.
    public static String[] input;       // Stores the user's input.
    public static String[] arguments;   // Stores the arguments given (used in PLAYLISTS command).
    public static String command;       // Stores the current command.
    public static String param;         // Stores the current parameter (used in PLAYLISTS command).


    public static void main(String[] args) throws IOException, InterruptedException {
        // First, we parse the arguments given.
        if (args.length != 0) {
            for (int i = 0; i < args.length; i++) {
                // If a different access path is given, we update ours.
                if (args[i].equals("-access")) {
                    access = args[i+1];
                // If a different API endpoint is given, we update ours..
                } else if (args[i].equals("-resource")) {
                    resource = args[i+1];
                // If a different num of entries per page is given, we update ours.
                } else if (args[i].equals("-page")) {
                    try {
                        entries = Integer.parseInt(args[i+1]);
                    } catch(Exception e) {
                        System.out.println(e);
                    }
                }
            }
        }

        // The scanner is initialized to get the input.
        Scanner scanner = new Scanner(System.in);
        // We initialize the message that appears if a command is called when
        // no authorization has been made.
        String no_auth = "Please, provide access for application.";

        // We begin the program loop.
        while (go) {
            // If the previous command was not a page change:
            if (pg_com.equals("")) {
                // We get the user's input and parse any arguments given.
                input = scanner.nextLine().split(" ");
                arguments = new String[input.length - 1];
                // Command stores the chosen instruction.
                command = input[0];
                for (int i = 0, k = 0; i < input.length; i++) {
                    if (i != 0) {
                        arguments[k] = input[i];
                        k++;
                    }
                }
                // Param stores any given parameters from the arguments.
                param = String.join(" ", arguments);

                // If the command was AUTH...
                if (command.equals("auth")) {
                    // ...and it is still needed...
                    if (!auth) {
                        // ... we call the authorization function.
                        Controller.authorize();
                    // ...and it is already authorized, we tell the user that.
                    } else {
                        System.out.println("Already authorized");
                    }
                    continue;
                }
            // If the previous command WAS a page change:
            } else {
                // We state that the last command and parameters used will be used again.
                command = last_com;
                param = last_param;
            }

            // If the command was EXIT, we change the GO value to FALSE and end the loop.
            if (command.equals("exit")) {
                go = false;
                break;
            }

            // If the user is authorized, then the rest of the commands can be used.
            if (auth) {
                switch (command) {
                    // If the command was NEW:
                    case "new":
                        // Update the last command and current page.
                        last_com = "new";
                        cur_page = 1;
                        // We call the NEW function.
                        Controller.showNew();
                        // We set that the last command was not a page change.
                        pg_com = "";
                        break;
                    case "categories":
                        // Update the last command and current page.
                        last_com = "categories";
                        cur_page = 1;
                        // We call the CATEGORIES function.
                        Controller.showCategories();
                        // We set that the last command was not a page change.
                        pg_com = "";
                        break;
                    case "playlists":
                        // Update the last command and current page.
                        last_com = "playlists";
                        cur_page = 1;
                        // If the parameter was given...
                        if (arguments.length != 0) {
                            // ...then we update the last parameter and continue.
                            last_param = param;
                            // We call the PLAYLISTS function.
                            Controller.showPlaylists(param);
                        // If the parameter was not given...
                        } else {
                            // ...the error is displayed as an unknown name.
                            System.out.println("Unknown category name.");
                        }
                        // We set that the last command was not a page change.
                        pg_com = "";
                        break;
                    case "featured":
                        // Update the last command and current page.
                        last_com = "featured";
                        cur_page = 1;
                        // We call the FEATURED function.
                        Controller.showFeatured();
                        // We set that the last command was not a page change.
                        pg_com = "";
                        break;
                    case "next":
                        // If there are no pages initialized, we tell that to the user.
                        if (last_com == "") {
                            System.out.println("No pages to view.");
                        // If this is the last page, we tell that to the user.
                        } else if (cur_page == Controller.amt_pages) {
                            System.out.println("No more pages.");
                        // Otherwise...
                        } else {
                            // ...we update the current page and state that the last command
                            // was a page change.
                            cur_page += 1;
                            pg_com = "next";
                            // We then call the corresponding function to display the next page.
                            pagination();
                        }
                        break;
                    case "prev":
                        // If there are no pages initialized, we tell that to the user.
                        if (last_com == "") {
                            System.out.println("No pages to view.");
                        // If this is the first page, we tell that to the user.
                        } else if (cur_page == 1) {
                            System.out.println("No more pages.");
                        // Otherwise...
                        } else {
                            // ...we update the current page and state that the last command
                            // was a page change.
                            cur_page -= 1;
                            pg_com = "prev";
                            // We then call the corresponding function to display the previous page.
                            pagination();
                        }
                        break;
                    // In case of a command not recognized, we tell that to the user.
                    default:
                        System.out.println("Command not recognized. Try again.");
                }
            // If the user was not authorized, then we print that message.
            } else {
                System.out.println(no_auth);
            }
        }
    }

    // This functions calls the corresponding command when a page change was made.
    public static void pagination() throws IOException, InterruptedException {
        // For every command, we do the same as if it would be the first time calling it,
        // but without updating the last command used or the current page.
        switch(last_com) {
            case "new":
                Controller.showNew();
                pg_com = "";
                break;
            case "categories":
                Controller.showCategories();
                pg_com = "";
                break;
            case "playlists":
                Controller.showPlaylists(last_param);
                pg_com = "";
                break;
            case "featured":
                Controller.showFeatured();
                pg_com = "";
                break;
        }
    }
}

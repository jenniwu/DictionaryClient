package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.exception.DictConnectionException;
import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.*;
import java.net.Socket;
import java.util.*;

import static java.lang.Integer.parseInt;



/**
 * Created by Jonatan on 2017-09-09.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private static final int GOOD_CONNECTION=220;
    private static final int HAVE_DB=110;
    private static final int HAVE_STRAT=111;
    private static final int HAVE_NO_MATCHES=552;
    private static final int HAVE_DEF=150;
    private static final int DEF=151;
    private static final int OK=250;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;

    private Map<String, Database> databaseMap = new LinkedHashMap<String, Database>();

    /** Establishes a new connection with a DICT server using an explicit host and port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {
        // TODO Add your code here/ Richard
        try {
            //Connect to server
            this.socket = new Socket(host, port);

            //Get welcome msg and bind the socket to application IO
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),true);

            //Verify connection is Good
            String incomingString=input.readLine();
            int status=parseInt(incomingString.substring(0,3));
            //System.out.println("Connected: "+getStatusFromLine(incomingString));
            if(status!=GOOD_CONNECTION){
                throw new DictConnectionException("Cannot connect to "+host);
            }
        }catch(IOException e){
            //System.err.println("Couldn't get I/O for the connection to " +host);
            throw new DictConnectionException("Connection Problem:"+host);
        }

    }

    /** Establishes a new connection with a DICT server using an explicit host, with the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection can't be established, or the messages
     * don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /** Sends the final QUIT message and closes the connection with the server. This function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the connection.
     *
     */
    public synchronized void close() {
        // TODO Add your code here/ Richard
        try {
            //Send QUIT msg
            output.println("QUIT");
            this.socket.close();
        }catch (IOException e){
            //Ignore Everything
        }
    }

    /** Requests and retrieves all definitions for a specific word.
     *
     * @param word The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 definitions in the first database that has a definition for the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database) throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();
        getDatabaseList(); // Ensure the list of databases has been populated

        // TODO Add your code here
        try {
            //Send msg to server, common to all similar methods
            output.println("DEFINE " + database.getName() + " " + word);

            //Status check, common to all similar methods
            String incomingString = input.readLine();
            int status = getStatusFromLine(incomingString);
            if (status != HAVE_DEF){
                throw new DictConnectionException("There are no definitions.");
            }

            // iterate through list of definitions
            while (true) {
                incomingString = input.readLine();
                if (getStatusFromLine(incomingString) == OK) {
                    break;
                } else {
                    if (getStatusFromLine(incomingString) == DEF) {
                        Definition definition = parseDefinition(word, incomingString);
                        set.add(definition);
                    }
                }
            }
        } catch (IOException e) {
            //System.out.println("IO exception thrown from getDefinitions()");//Such messages were required to be removed in Jonatan's 313 course. I will check with him again.
            throw new DictConnectionException("IO exception thrown from getDefinitions.");//Sept.16.Modify
        }

        return set;
    }

    /** Parses the definition of a word from a dictionary.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param header   The current line of input from the Input Stream.
     * @return A definition of the word.
     */
    private Definition parseDefinition(String word, String header) {

        //Extract the header of a definition by identifying whitespaces
        int first = header.indexOf(" ");
        int second = header.indexOf(" ", first+1);
        int third = header.indexOf(" ", second+1);
        String dict = header.substring(second+1, third);

        //Initialize a definition
        Database database = databaseMap.get(dict);
        Definition def = new Definition(word, database);
        def.setDefinition(null);

        try {
            while(true) {
                String incomingString = input.readLine();
                if (incomingString.equals(".")) {
                    break;
                } else def.appendDefinition(incomingString);
            }
        } catch (IOException e) {
            //System.out.println("IO exception thrown from parseDefinition()");
        }

        return def;
    }

    /** Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special database may be specified,
     *                 indicating either that all regular databases should be used (database name '*'), or that only
     *                 matches in the first database that has a match for the word should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database) throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // TODO Add your code here
        try {
            output.println("MATCH " + database.getName() + " " + strategy.getName() + " " + word);

            String incomingString = input.readLine();
            int status = getStatusFromLine(incomingString);
            if (status == HAVE_NO_MATCHES)
                throw new DictConnectionException("There are no matches.");

            // iterate through list of matches
            while (true) {
                incomingString = input.readLine();
                if (incomingString.equals(".")) {
                    input.readLine();   // "250 ok"
                    break;
                } else {
                    int start = incomingString.indexOf("\"") + 1;
                    String s = incomingString.substring(start).replaceAll("\"", "");
                    set.add(s);
                }
            }

        } catch (IOException e) {
            //System.out.println("IO exception thrown from getMatchList()");
            throw new DictConnectionException("IO exception thrown from getMatchList().");//Sept.16.Modify throw exp if connection interrupts
        }

        return set;
    }

    /** Requests and retrieves a list of all valid databases used in the server. In addition to returning the list, this
     * method also updates the local databaseMap field, which contains a mapping from database name to Database object,
     * to be used by other methods (e.g., getDefinitionMap) to return a Database object based on the name.
     *
     * @return A collection of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Collection<Database> getDatabaseList() throws DictConnectionException {

        if (!databaseMap.isEmpty()) return databaseMap.values();

        // TODO Add your code here
        try {
            output.println("SHOW DB");
            String incomingString = input.readLine();

            int status = getStatusFromLine(incomingString);
            if (status != HAVE_DB)
                throw new DictConnectionException("There are no databases.");

            // iterate through list of databases
            while (true) {
                incomingString = input.readLine();
                if (incomingString.equals(".")) {
                    input.readLine();   // "250 ok"
                    break;
                } else {
                    String[] pair = incomingString.split(" ", 2);//Parse the list
                    pair[1] = pair[1].replace("\"", "");
                    Database d = new Database(pair[0], pair[1]);
                    databaseMap.put(pair[0], d);
                }
            }
        } catch (IOException e) {
            //System.out.println("IO exception thrown from getDatabaseList");
            throw new DictConnectionException("IO exception thrown from getDatabaseList.");//Sept.16.Modify throw exp if connection interrupts
        }

        return databaseMap.values();
    }

    /** Requests and retrieves a list of all valid matching strategies supported by the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();

        // TODO Add your code here
        if (!set.isEmpty()) return set;

        try {
            output.println("SHOW STRAT");
            String incomingString = input.readLine();

            int status = getStatusFromLine(incomingString);
            if (status != HAVE_STRAT)
                throw new DictConnectionException("There are no strategies available.");

            // iterate through list of strategies
            while (true) {
                incomingString = input.readLine();
                if (incomingString.equals(".")) {
                    input.readLine();   // "250 ok"
                    break;
                } else {
                    String[] pair = incomingString.split(" ", 2);
                    pair[1] = pair[1].replace("\"", "");
                    MatchingStrategy ms = new MatchingStrategy(pair[0], pair[1]);
                    set.add(ms);
                }
            }
        } catch (IOException e) {
            //System.out.println("IO exception thrown from getStrategyList()");
            throw new DictConnectionException("IO exception thrown from getStrategyList()"); //Sept.16.Modify
        }

        return set;
    }
    //Given a line of returned message, extract the status code at an integer
    private int getStatusFromLine(String aLine){
        return parseInt(aLine.substring(0,3));
    }
}

package se.troed.plugin.Courier;

import com.google.common.io.Files;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Flatfile now, database later
 * I'm quite sure I could get rid of messageids by using other primitives 
 * "delivered" and "read" are slightly tricky. Delivered mail sets newmail to false, even when not read.
 * (and of course delivered=false and read=true is an invalid combination should it arise)
 *
 * courierclaimedmap: mapid       # 17 chars = cannot clash with any playername
 * receiver1:
 *   newmail: true/false          <-- makes some things faster but others slow
 *   messageids: 42,73,65         <-- get/setIntegerList, although it currently doesn't look this pretty in the yml
 *   mapid42:
 *     sender:
 *     message:
 *     date:
 *     delivered:
 *     read:
 *   mapid73:
 *     sender:
 *     message:
 *     date:
 *     delivered:
 *     read:
 *   mapid65:
 *     sender:
 *     message:
 *     date:
 *     delivered:
 *     read:
 * receiver2:
 *   ...
 *
 */
public class CourierDB {
    private static final String FILENAME = "messages.yml";
    private final Courier plugin;
    private YamlConfiguration mdb;
    
    CourierDB(Courier p) {
        plugin = p;    
    }

    // reading the whole message db into memory, is that a real problem?
    // returns true if there already was a db
    boolean load() throws IOException {
        File db = new File(plugin.getDataFolder(), FILENAME);
        mdb = new YamlConfiguration();
        if(db.exists()) {
            try {
                mdb.load(db);
            } catch (InvalidConfigurationException e) {
                // this might be a MacRoman (or other) encoded file and we're running under a UTF-8 default JVM
                mdb = loadNonUTFConfig(db);
                if(mdb == null) {
                    throw new IOException("Could not read Courier database!");
                }
            } catch (Exception e) {
                mdb = null;
//                e.printStackTrace();
                throw new IOException("Could not read Courier database!");
            }
            return true;
        }
        return false;
    }

    // see http://forums.bukkit.org/threads/friends-dont-let-friends-use-yamlconfiguration-loadconfiguration.57693/
    // manually load as MacRoman if possible, we'll force saving in UTF-8 later
    // Testing shows Apple Java 6 with default-encoding utf8 finds a "MacRoman" charset
    // OpenJDK7 on Mac finds a "x-MacRoman" charset.
    //
    // "Every implementation of the Java platform is required to support the following standard charsets. Consult the release documentation for your implementation to see if any other charsets are supported. The behavior of such optional charsets may differ between implementations.
    // US-ASCII, ISO-8859-1, UTF-8 [...]"
    //
    // http://www.alanwood.net/demos/charsetdiffs.html - compares ansi, iso and macroman
    // NOTE: This method isn't pretty and should - really - be recoded.
    private YamlConfiguration loadNonUTFConfig(File db) {
        InputStreamReader reader; 
        try {
            Charset cs;
            try {
                // This issue SHOULD be most common on Mac, I think, assume MacRoman default
                cs = Charset.forName("MacRoman");
            } catch (Exception e) {
                cs = null;
            }
            if(cs == null) {
                try {
                    // if no MacRoman can be found in the JVM, assume ISO-8859-1 is the closest match
                    cs = Charset.forName("ISO-8859-1");
                } catch (Exception e) {
                    return null;
                }
            }
            plugin.getCConfig().clog(Level.WARNING, "Trying to convert message database from " + cs.displayName() + " to UTF-8");
            reader = new InputStreamReader(new FileInputStream(db), cs);
        } catch (Exception e) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        BufferedReader input = new BufferedReader(reader);

        try {
            String line;

            while ((line = input.readLine()) != null) {
                builder.append(line);
                builder.append('\n');
            }
        } catch (Exception e) {
            return null;
        } finally {
            try {
                input.close();
            } catch (Exception e) {
                // return null;
            }
        }
        mdb = new YamlConfiguration();
        try {
            mdb.loadFromString(builder.toString());
        } catch (Exception e) {
            mdb = null;
            e.printStackTrace();
        }
        return mdb;
    }

    // if filename == null, uses default
    // (this makes making backups really easy)
    void save(String filename) {
        boolean ret = false;
        if(mdb != null) {
            File db = new File(plugin.getDataFolder(), filename != null ? filename : FILENAME);
            try {
//                saveUTFConfig(db, mdb);
                mdb.save(db);
                ret = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // even if we're run under a JVM with non-utf8 default encoding, force it
    // at least that was the idea, but on Mac it's still read back using MacRoman. No automatic switching to UTF-8
    public void saveUTFConfig(File file, YamlConfiguration yaml) throws IOException {
        if(yaml != null) {
            Charset cs;
            try {
                cs = StandardCharsets.UTF_8;
            } catch (Exception e) {
                throw new IOException("UTF-8 not a supported charset");
            }

            Files.createParentDirs(file);
            String data = yaml.saveToString();

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), cs)) {
                writer.write(data);
            }
        }
    }

    // retrieves the version of our database format, -1 if it doesn't exist
    // 1 = v1.0.0
    int getDatabaseVersion() {
        if(mdb == null) {
            return -1;
        }
        return mdb.getInt("courierdatabaseversion", -1);
    }

    void setDatabaseVersion(int v) {
        if(mdb == null) {
            return;
        }
        mdb.set("courierdatabaseversion", v);
    }

    // retrieves what we think is our specially allocated Map
    public int getCourierMapId() {
        if(mdb == null) {
            return -1;
        }
        return mdb.getInt("courierclaimedmap", -1);
    }
    
    void setCourierMapId(int mapId) {
        if(mdb == null) {
            return;
        }
        mdb.set("courierclaimedmap", mapId);
    }

    public boolean sendMessage(int id, String recipient, String sender) {
        boolean ret = false;
        if(mdb == null || recipient == null || sender == null) {
            return false;
        }

        recipient = recipient.toLowerCase();
        
        // nothing to say the player who wants to send a picked up Letter is the one with it in her storage
        // but if player2 steals a letter written by player1 and immediately sends to player3, player1
        // should not be listed as sender. See outcommented getSender() below

        String origin = getPlayer(id);

        if(origin != null) {
            // alright, sign over to a specific receiver
//            String s = getSender(origin, id);
            String m = getMessage(origin, id);
            int date = getDate(origin, id);

            List<Integer> messageids = mdb.getIntegerList(recipient + ".messageids");
            if(!messageids.contains(id)) { // I should move to a non-duplicate storage type ..
                messageids.add(id);
            }
            mdb.set(recipient + ".messageids", messageids);
            mdb.set(recipient + "." + id + ".sender", sender);
            mdb.set(recipient + "." + id + ".message", m);
            mdb.set(recipient + "." + id + ".date", date);
            // new messages can't have been delivered
            mdb.set(recipient + "." + id + ".delivered", false);
            // new messages can't have been read
            mdb.set(recipient + "." + id + ".read", false);

            // since there's at least one new message, set newmail to true
            mdb.set(recipient + ".newmail", true);

            // if we send to ourselves, don't delete what we just added
            if(!recipient.equalsIgnoreCase(origin)) {
                // "atomic" remove
                messageids = mdb.getIntegerList(origin + ".messageids");
                // safety check
                messageids.remove(Integer.valueOf(id));
                mdb.set(origin + ".messageids", messageids);
                mdb.set(origin + "." + id, null);
            }

            this.save(null);
            ret = true;
        }
        return ret;    
    }

    public boolean storeMessage(int id, String s, String m, int d) {
        if(mdb == null || s == null || m == null) {
            return false;
        }

        String skey = s.toLowerCase();
        String origin = getPlayer(id);

        // update messageids
        List<Integer> messageids = mdb.getIntegerList(skey + ".messageids");
        if(!messageids.contains(id)) { // I should move to a non-duplicate storage type ..
            messageids.add(id);
        }
        mdb.set(skey + ".messageids", messageids);

        mdb.set(skey + "." + id + ".sender", s);
        mdb.set(skey + "." + id + ".message", m);
        mdb.set(skey + "." + id + ".date", d);
        mdb.set(skey + "." + id + ".delivered", true);
        mdb.set(skey + "." + id + ".read", true);
        // we do not change .newmail when storing in our own storage, of course

        if(origin != null && !s.equalsIgnoreCase(origin)) {
            // the current writer of this letter was not the same as the last, make sure it's moved
            messageids = mdb.getIntegerList(origin + ".messageids");
            if(messageids != null) { // safety check
                messageids.remove(Integer.valueOf(id));
            }
            mdb.set(origin + ".messageids", messageids);
            mdb.set(origin + "." + id, null);
        }
        
        this.save(null); // save after each stored message currently

        return true;
    }

    // this method is called when we detect a database version with case sensitive keys
    // it simply lowercases all Player name keys
    void keysToLower() {
        if(mdb == null) {
            return;
        }

        // just for safety, back up db first, and don't allow the backup to be overwritten if it exists
        // (if this method throws exceptions most admins will just likely try a few times .. )
        String backup = FILENAME + ".100.backup";
        File db = new File(plugin.getDataFolder(), backup);
        if(!db.exists()) {
            this.save(backup);
        }
        
        Set<String> players = mdb.getKeys(false);
        for (String r : players) {
            String rlower = r.toLowerCase();

            if(!r.equals(rlower)) {
                // this receiver needs full rewriting
                boolean newmail = mdb.getBoolean(r + ".newmail");
                List<Integer> messageIDs = mdb.getIntegerList(r + ".messageids");
                List<Integer> newMessageIDs = mdb.getIntegerList(rlower + ".messageids");
                for(Integer id : messageIDs) {
                    // fetch a message
                    String s = mdb.getString(r + "." + id + ".sender");
                    String m = mdb.getString(r + "." + id + ".message");
                    int date = mdb.getInt(r + "." + id + ".date");
                    boolean delivered = mdb.getBoolean(r + "." + id + ".delivered");
                    boolean read = mdb.getBoolean(r + "." + id + ".read");
                    
                    mdb.set(rlower + "." + id + ".sender", s);
                    mdb.set(rlower + "." + id + ".message", m);
                    mdb.set(rlower + "." + id + ".date", date);
                    mdb.set(rlower + "." + id + ".delivered", delivered);
                    mdb.set(rlower + "." + id + ".read", read);

                    newMessageIDs.add(id);

                    mdb.set(r + "." + id, null); // delete old message
                }
                mdb.set(rlower + ".messageids", newMessageIDs);
                mdb.set(rlower + ".newmail", newmail);

                mdb.set(r, null); // delete the old entry
            }
        }
        this.save(null);
    }
    
    // used for legacy Letter conversion only
    public boolean storeDate(int id, int d) {
        if(mdb == null) {
            return false;
        }
        
        String player = getPlayer(id);
        if(player == null) {
            return false; // this would be bad
        }
        mdb.set(player + "." + id + ".date", d);

        return true;
    }
    
    // currently used for legacy Letter conversion only, but it is generalized
    public void changeId(int oldid, int newid) {
        if(mdb == null) {
            return;
        }
        
        String r = getPlayer(oldid);
        String s = getSender(r, oldid);
        String m = getMessage(r, oldid);
        int date = getDate(r, oldid);
        boolean delivered = getDelivered(r, oldid);
        boolean read = getRead(r, oldid);
        
        List<Integer> messageIDs = mdb.getIntegerList(r + ".messageids");
        messageIDs.add(newid);
        // "atomic" add
        mdb.set(r + ".messageids", messageIDs);
        mdb.set(r + "." + newid + ".sender", s);
        mdb.set(r + "." + newid + ".message", m);
        mdb.set(r + "." + newid + ".date", date);
        mdb.set(r + "." + newid + ".delivered", delivered);
        mdb.set(r + "." + newid + ".read", read);

        // "atomic" remove
        messageIDs.remove(Integer.valueOf(oldid)); // caught out by ArrayList.remove(Object o) vs remove(int i) ...
        mdb.set(r + ".messageids", messageIDs);
        mdb.set(r + "." + oldid, null);
    }

    public boolean undeliveredMail(String recipient) {
        //noinspection SimplifiableIfStatement
        if(mdb == null || recipient == null) {
            return false;
        }
        
        recipient = recipient.toLowerCase();

        return mdb.getBoolean(recipient + ".newmail");
    }

    // runs through messageids, sets all unread messages to undelivered
    // returns false when there are no unread messages
    public boolean deliverUnreadMessages(String r) {
        if(mdb == null || r == null) {
            return false;
        }

        r = r.toLowerCase();

        boolean newMail = false;
        List<Integer> messageids = mdb.getIntegerList(r + ".messageids");
        for(Integer id : messageids) {
            boolean read = mdb.getBoolean(r + "." + id + ".read");
            if(!read) {
                mdb.set(r + "." + id + ".delivered", false);
                newMail = true;
            }
        }
        if(newMail) {
            mdb.set(r + ".newmail", newMail);
        }
        return newMail;
    }

    // runs through messageids, finds a message not read and returns the corresponding id
    // returns -1 on failure
    public int unreadMessageId(String r) {
        if(mdb == null || r == null) {
            return -1;
        }

        r = r.toLowerCase();

        List<Integer> messageids = mdb.getIntegerList(r + ".messageids");
        for(Integer id : messageids) {
            boolean read = mdb.getBoolean(r + "." + id + ".read");
            if(!read) {
                return id;
            }
        }
        return -1;
    }

    // runs through messageids, finds a message not delivered and returns the corresponding id
    // returns -1 on failure
    public int undeliveredMessageId(String r) {
        if(mdb == null || r == null) {
            return -1;
        }

        r = r.toLowerCase();

        List<Integer> messageids = mdb.getIntegerList(r + ".messageids");
        if(messageids != null) {
            for(Integer id : messageids) {
                boolean delivered = mdb.getBoolean(r + "." + id + ".delivered");
                if(!delivered) {
                    return id;
                }
            }
        }

        // if we end up here, for any reason, it means there are no undelivered messages
        mdb.set(r + ".newmail", false);
        return -1;
    }

    // removes a single Letter from the database
    public boolean deleteMessage(short id) {
        if(id == -1 || mdb == null) {
            return false;
        }

        String r = getPlayer(id);
        if(r == null) {
            return false;
        }

        List<Integer> messageids = mdb.getIntegerList(r + ".messageids");

        // "atomic" remove
        messageids.remove(Integer.valueOf(id)); // caught out by ArrayList.remove(Object o) vs remove(int i) ...
        mdb.set(r + ".messageids", messageids);
        mdb.set(r + "." + id, null);

        // todo: If our Letter had been delivered to another Player then remove that delivered info for them too.
        // seems not critical. new letters will set delivered to false even if ID is reused

        return true;
    }

    // does this id exist in the database
    // todo: horribly inefficient compared to just calling getPlayer() - due to using YAML instead of SQLite
    //       in this mergeback from the v1.2.0 branch
    boolean isValid(int id) {
        if(id == -1 || mdb == null) {
            return false;
        }

        Set<String> strings = mdb.getKeys(false);
        for (String key : strings) {
            List<Integer> messageids = mdb.getIntegerList(key + ".messageids");
            if (messageids != null && messageids.contains(id)) {
                return true;
            }
        }

        return false;
    }

    // finds a specific messageid and returns associated player
    String getPlayer(int id) {
        if(id == -1 || mdb == null) {
            return null;
        }
        
        Set<String> strings = mdb.getKeys(false);
        for (String key : strings) {
            List<Integer> messageids = mdb.getIntegerList(key + ".messageids");
            if (messageids.contains(id)) {
                return key;
            }
        }
        return null;
    }
    
    public String getSender(String recipient, int id) {
        if(mdb == null || recipient == null) {
            return null;
        }

        return mdb.getString(recipient.toLowerCase() + "." + id + ".sender");
    }
    
    public String getMessage(String recipient, int id) {
        if(mdb == null || recipient == null) {
            return null;
        }

        return mdb.getString(recipient.toLowerCase() + "." + id + ".message");
    }

    private boolean getDelivered(String r, int id) {
        //noinspection SimplifiableIfStatement
        if(mdb == null || r == null || id==-1) {
            return false;
        }

        r = r.toLowerCase();

        return mdb.getBoolean(r + "." + id + ".delivered");
    }

    // unexpected side effect, we end up here if player1 takes a message intended for player2
    // exploit or remove logging of it?
    public boolean setDelivered(String r, int id) {
        if(mdb == null || r == null || id==-1) {
            return false;
        }

        r = r.toLowerCase();

        mdb.set(r + "." + id + ".delivered", true);
        undeliveredMessageId(r); // DIRTY way of making sure "newmail" is cleared
        return true;
    }

    int getDate(String r, int id) {
        if(mdb == null || r == null) {
            return -1;
        }

        r = r.toLowerCase();

        return mdb.getInt(r + "." + id + ".date");
    }

    boolean getRead(String r, int id) {
        //noinspection SimplifiableIfStatement
        if(mdb == null || r == null || id==-1) {
            return false;
        }

        r = r.toLowerCase();

        return mdb.getBoolean(r + "." + id + ".read");
    }

    public boolean setRead(String reader, int id) {
        if(mdb == null || reader == null || id==-1) {
            return false;
        }

        reader = reader.toLowerCase();

        mdb.set(reader + "." + id + ".read", true);
        return true;
    }

    // returns the first available id, or -1 when we're fatally out of them (or db error .. hmm)
    // expected to be called seldom (at letter creation) and is allowed to be slow
    // obvious caching/persisting of TreeSet possible
    public int generateUID() {
        if(mdb == null) {
            return -1;
        }
        TreeSet<Integer> sortedSet = new TreeSet<>();
        Set<String> players = mdb.getKeys(false);
        for (String player : players) {
            List<Integer> messageIDs = mdb.getIntegerList(player + ".messageids");
            // add all messageids found for this player to our ordered set
            sortedSet.addAll(messageIDs);
        }
        // make sure we don't enter negative number territory
        // todo: introduce "fuzziness" making nextId less predictable
        for(int i=Courier.MIN_ID; i<Courier.MAX_ID; i++) {
            if(sortedSet.add(i)) {
                // i wasn't in the set
                return i;
            }
        }
        return -1;
    }
}

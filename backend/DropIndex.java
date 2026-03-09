import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DropIndex {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/tba_waad_system";
        String user = "postgres";
        String password = "12345";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Dropping index/constraint idx_claims_duplicate_prevention...");
            try {
                stmt.execute("ALTER TABLE claims DROP CONSTRAINT IF EXISTS idx_claims_duplicate_prevention");
                System.out.println("Dropped constraint");
            } catch (Exception e) {
                System.out.println("Failed to drop constraint: " + e.getMessage());
            }

            try {
                stmt.execute("DROP INDEX IF EXISTS idx_claims_duplicate_prevention");
                System.out.println("Dropped index");
            } catch (Exception e) {
                System.out.println("Failed to drop index: " + e.getMessage());
            }

            System.out.println("Done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package com.waad.tba;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckDuplicates {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/tba_waad_system", "postgres", "root");
            Statement stmt = conn.createStatement();
            
            // Query for duplicate names
            System.out.println("--- Checking Duplicate Names ---");
            ResultSet rs = stmt.executeQuery("SELECT full_name, COUNT(*) FROM members WHERE active = true GROUP BY full_name HAVING COUNT(*) > 1 LIMIT 10");
            while (rs.next()) {
                System.out.println(rs.getString("full_name") + " -> " + rs.getInt("count"));
            }
            
            // Query for similar names using simple replace
            System.out.println("--- Checking Similar Names ---");
            rs = stmt.executeQuery("SELECT REPLACE(REPLACE(full_name, 'أ', 'ا'), 'ة', 'ه') as norm, COUNT(*) FROM members WHERE active = true GROUP BY norm HAVING COUNT(*) > 1 LIMIT 10");
            while (rs.next()) {
                System.out.println(rs.getString("norm") + " -> " + rs.getInt("count"));
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

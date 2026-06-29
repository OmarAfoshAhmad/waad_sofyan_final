package com.waad.tba;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class CheckAbdulmunim {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/tba_waad_system", "postgres", "root");
            Statement stmt = conn.createStatement();
            
            System.out.println("--- Finding Abdulmunim ---");
            ResultSet rs = stmt.executeQuery("SELECT id, full_name, card_number, parent_id, employer_id, active, status FROM members WHERE full_name LIKE '%عبدالمنعم%'");
            while (rs.next()) {
                System.out.println(rs.getLong("id") + " | " + 
                                   rs.getString("full_name") + " | " + 
                                   rs.getString("card_number") + " | " + 
                                   rs.getString("parent_id") + " | " + 
                                   rs.getString("employer_id") + " | " + 
                                   rs.getBoolean("active") + " | " + 
                                   rs.getString("status"));
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

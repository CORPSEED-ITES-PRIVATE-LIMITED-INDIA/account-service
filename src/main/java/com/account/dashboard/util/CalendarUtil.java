package com.account.dashboard.util;

import java.util.Calendar;
import java.util.Date;

import org.springframework.stereotype.Service;

@Service
public class CalendarUtil {
	
	public static Date addDayInDate(Date d) {
		
        Calendar calendar = Calendar.getInstance();
        
        // Print the current date
        System.out.println("Current Date: " + calendar.getTime());
        
        // Add one day to the current date
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        
        // Get the new date
        Date newDate = calendar.getTime();
        
        
        System.out.println("newDate . .. . . "+newDate);
        return newDate;
	}

}

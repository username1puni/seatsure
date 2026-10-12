package com.seatsure.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sends browser routes like /movies/3 to the React app so a page refresh doesn't 404. */
@Controller
public class SpaController {

    @GetMapping({"/login", "/register", "/movies/**", "/shows/**", "/checkout/**", "/bookings/**", "/admin/**"})
    public String forward() {
        return "forward:/index.html";
    }
}

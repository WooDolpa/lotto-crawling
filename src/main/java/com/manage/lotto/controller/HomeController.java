package com.manage.lotto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * packageName : com.manage.lotto.controller
 * className : HomeController
 * user : jwlee
 * date : 2026. 1. 19.
 * description :
 */
@Controller
public class HomeController {

    @GetMapping(path = "/")
    public String index() {
        return "index";
    }

    @GetMapping("/prediction")
    public String prediction() {
        return "redirect:/lotto/prediction";
    }

    @GetMapping("/lotto/prediction")
    public String lottoPrediction() {
        return "prediction";
    }

    @GetMapping("/lotto/register")
    public String register() { return "register"; }

    @GetMapping("/validation")
    public String validation() {
        return "validation";
    }
}

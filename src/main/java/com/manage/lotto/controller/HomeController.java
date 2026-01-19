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
}

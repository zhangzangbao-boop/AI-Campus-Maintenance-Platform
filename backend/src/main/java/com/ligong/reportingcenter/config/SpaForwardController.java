package com.ligong.reportingcenter.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {
        "/login",
        "/stuhome",
        "/workerhome",
        "/adminhome"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
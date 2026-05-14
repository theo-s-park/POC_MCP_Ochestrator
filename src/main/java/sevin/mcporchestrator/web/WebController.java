package sevin.mcporchestrator.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/", "/servers"})
    public String servers() { return "servers"; }

    @GetMapping("/tools")
    public String tools() { return "tools"; }

    @GetMapping("/demo")
    public String demo() { return "demo"; }

    @GetMapping("/agent")
    public String agent() { return "agent"; }

    @GetMapping("/backoffice")
    public String backoffice() { return "backoffice"; }
}

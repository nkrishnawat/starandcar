package com.resilientechnology.starandcar.controller.owner;

import com.resilientechnology.starandcar.record.FeedbackVO;
import com.resilientechnology.starandcar.service.owner.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("feedback")
public class FeedbackController {

    @Autowired
    FeedbackService feedbackService;

    @PostMapping(value = "save")
    public boolean saveFeedback(@Valid @ModelAttribute FeedbackVO feedbackVO) {
        return feedbackService.saveFeedback(feedbackVO);
    }
}
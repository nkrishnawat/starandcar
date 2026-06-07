package com.resilientechnology.starandcar.service.owner;

import com.resilientechnology.starandcar.record.FeedbackVO;
import com.resilientechnology.starandcar.entity.Feedback;
import com.resilientechnology.starandcar.repository.owner.FeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {
    @Autowired
    FeedbackRepository feedbackRepository;

    public boolean saveFeedback(FeedbackVO feedbackVO) {
        return feedbackRepository.save(to_entity(feedbackVO));
    }

    private Feedback to_entity(FeedbackVO feedbackVO) {
        return Feedback.builder().description(feedbackVO.getDescription()).build();
    }
}
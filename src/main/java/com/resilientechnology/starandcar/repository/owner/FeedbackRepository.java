package com.resilientechnology.starandcar.repository.owner;

import com.resilientechnology.starandcar.entity.Feedback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;

@Repository
public class FeedbackRepository {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    final String INSERT_FEEDBACK = """
        INSERT INTO FEEDBACK
        (description, CREATED_DATE)
        VALUES (?, ?)
    """;

    public boolean save(Feedback feedback) {
        return jdbcTemplate.update(INSERT_FEEDBACK, feedback.getDescription(), Date.from(Instant.now())) > 0;
    }
}
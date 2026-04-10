package com.h2ai.insights.entity;

import com.h2ai.insights.enums.Gender;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class User {

    private Integer age;
    private String name;
    @Enumerated(EnumType.STRING)
    private Gender gender;


    

}

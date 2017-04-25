package com.pe.grum.api.cucumber.stepdefs;

import com.pe.grum.api.GrumApp;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.ResultActions;

import org.springframework.boot.test.context.SpringBootTest;

@WebAppConfiguration
@SpringBootTest
@ContextConfiguration(classes = GrumApp.class)
public abstract class StepDefs {

    protected ResultActions actions;

}

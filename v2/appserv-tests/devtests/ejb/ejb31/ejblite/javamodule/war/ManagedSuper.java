package com.acme;

import javax.annotation.*;
import javax.ejb.EJB;
import javax.annotation.Resource;
import javax.sql.DataSource;

public class ManagedSuper {

    @EJB StatelessBean s;
    @Resource(name="jdbc/__default") DataSource ds;

    @PostConstruct
    private void init() {
	System.out.println("In ManagedSuper::init() ");
    }

    @PreDestroy
    private void destroy() {
	System.out.println("In ManagedSuper::destroy() ");
    }

    public String toString() {
	return "ManagedSuper this = " + super.toString() + 
	    " s = " + s + " , ds = " + ds;

    }

}

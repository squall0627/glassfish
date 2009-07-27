package org.glassfish.tests.ejb.sample;

import java.util.Collection;
import java.util.Date;

import javax.ejb.Stateless;
import javax.annotation.security.PermitAll;
import javax.annotation.security.DenyAll;

import javax.persistence.PersistenceContext;
import javax.persistence.EntityManager;
import javax.persistence.Query;

/**
 * @author Jerome Dochez
 */
@Stateless
public class SimpleEjb {

    @PersistenceContext(unitName="test") EntityManager em;

    @PermitAll
    public String saySomething() {
        return "boo";
    }

    @PermitAll
    public String testJPA() {
        String result = null;
        Query q = em.createNamedQuery("SimpleEntity.findAll");
        Collection entities = q.getResultList();
        int s = entities.size();

        if (s < 10) {
            System.out.println("Record # " + (s + 1));
            SimpleEntity e = new SimpleEntity("Entity number " + (s + 1) + " created at " + new Date());
            em.persist(e);
            result = "Entity number " + (s + 1);
        } else {
            result = "10 entities created";
        }
        return result;

    }
}

/*
 * #%L
 * BroadleafCommerce USPS
 * %%
 * Copyright (C) 2009 - 2016 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
/*
 * Copyright 2008-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.broadleafcommerce.vendor.usps.dao;

import org.broadleafcommerce.common.persistence.EntityConfiguration;
import org.broadleafcommerce.vendor.usps.domain.USPSConfiguration;
import org.hibernate.ejb.QueryHints;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import java.util.List;

/**
 * 
 * @author Kelly Tisdell
 *
 */
public class USPSConfigurationDaoImpl implements USPSConfigurationDao {

    @PersistenceContext(unitName="blPU")
    protected EntityManager em;
    
    @Resource(name="blEntityConfiguration")
    protected EntityConfiguration entityConfiguration;
    
    @Override
    @SuppressWarnings("unchecked")
    public USPSConfiguration findUSPSConfiguration() {
        StringBuffer queryString = new StringBuffer("select uspsConfig from ");
        queryString.append(entityConfiguration.lookupEntityClass(USPSConfiguration.class.getName()).getName()).append(" uspsConfig");
        Query query = em.createQuery(queryString.toString());
        query.setMaxResults(1);
        query.setHint(QueryHints.HINT_CACHEABLE, true);
        List<USPSConfiguration> configs = query.getResultList();
        if (configs != null && configs.size() > 0) {
            return configs.get(0);
        }
        return null;
    }

    @Override
    public USPSConfiguration saveUSPSConfiguration(USPSConfiguration config) {
        return em.merge(config);
    }

    @Override
    public void deleteUSPSConfiguration(USPSConfiguration config) {
        em.remove(config);
    }
    
    @Override
    public USPSConfiguration createUSPSConfiguration() {
        return (USPSConfiguration)entityConfiguration.createEntityInstance(USPSConfiguration.class.getName());
    }

}

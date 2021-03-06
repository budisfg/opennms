/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2010-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.jasper.measurement;

import java.util.Map;

import net.sf.jasperreports.engine.JRDataset;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRRewindableDataSource;
import net.sf.jasperreports.engine.JRValueParameter;
import net.sf.jasperreports.engine.JasperReportsContext;
import net.sf.jasperreports.engine.query.JRAbstractQueryExecuter;

class MeasurementQueryExecutor extends JRAbstractQueryExecuter {

    private static final String SSL_PROPERTY_KEY = "org.opennms.netmgt.jasper.measurement.ssl.enable";

    private RemoteMeasurementDataSourceWrapper dataSourceCreator;

    protected MeasurementQueryExecutor(JasperReportsContext jasperReportsContext, JRDataset dataset, Map<String, ? extends JRValueParameter> parametersMap) {
        super(jasperReportsContext, dataset, parametersMap);
        if (dataset != null) {
            parseQuery();
        }
    }

    @Override
    protected String getParameterReplacement(String parameterName) {
        return String.valueOf(getParameterValue(parameterName));
    }

    @Override
    public boolean cancelQuery() throws JRException {
        return false;
    }

    @Override
    public JRRewindableDataSource createDatasource() throws JRException {
       return getDataSource(
               (String) getParameterValue(Parameters.URL),  // required parameter
               (String) getParameterValue(Parameters.USERNAME, true), // optional parameter
               (String) getParameterValue(Parameters.PASSWORD, true), // optional parameter
               getQueryString());
    }

    public JRRewindableDataSource getDataSource(final String url, final String username, final String password, final String query) throws JRException {
        if (dataSourceCreator == null) {
            dataSourceCreator = new RemoteMeasurementDataSourceWrapper(useSSL(), url, username, password);
        }
        return dataSourceCreator.createDataSource(query);
    }

    @Override
    public void close() {
        try {
            if (dataSourceCreator != null) {
                dataSourceCreator.disconnect();
            }
        } finally {
            dataSourceCreator = null;
        }
    }

    private static boolean useSSL() {
        return Boolean.valueOf(System.getProperty(SSL_PROPERTY_KEY, "false")).booleanValue();
    }
}

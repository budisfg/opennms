/*
 * This file is part of the OpenNMS(R) Application.
 *
 * OpenNMS(R) is Copyright (C) 2009-2010 The OpenNMS Group, Inc.  All rights reserved.
 * OpenNMS(R) is a derivative work, containing both original code, included code and modified
 * code that was published under the GNU General Public License. Copyrights for modified
 * and included code are below.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * Modifications:
 * 
 * 2010 Apr 25: Update for logging changes, minor fix-ups - jeffg@opennms.org
 * Created: August 28, 2009
 *
 * Copyright (C) 2009-2010 The OpenNMS Group, Inc.  All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * For more information contact:
 *      OpenNMS Licensing       <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 */
package org.opennms.netmgt.notifd;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.opennms.core.utils.Argument;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.ConfigFileConstants;
import org.opennms.netmgt.config.NotificationManager;
import org.opennms.netmgt.config.microblog.MicroblogProfile;
import org.opennms.netmgt.dao.MicroblogConfigurationDao;
import org.opennms.netmgt.dao.castor.DefaultMicroblogConfigurationDao;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Send notifications to a TwitterAPI-compatible microblog service.
 * 
 * @author <a href="mailto:jeffg@opennms.org">Jeff Gehlbach</a>
 * @author <a href="mailto:http://www.opennms.org">OpenNMS</a>
 */
public class MicroblogNotificationStrategy implements NotificationStrategy {
    private static final String UBLOG_PROFILE_NAME = "notifd";
    protected MicroblogConfigurationDao m_microblogConfigurationDao;
    protected MicroblogConfigurationDao m_configDao;
    
    public MicroblogNotificationStrategy() throws IOException {
        this(findDefaultConfigResource());
    }
    
    public MicroblogNotificationStrategy(Resource configResource) {
        m_configDao = new DefaultMicroblogConfigurationDao();
        ((DefaultMicroblogConfigurationDao)m_configDao).setConfigResource(configResource);
        ((DefaultMicroblogConfigurationDao)m_configDao).afterPropertiesSet();
        setMicroblogConfigurationDao(m_configDao);
    }

    public int send(List<Argument> arguments) {
        Twitter svc = buildUblogService(arguments);
        String messageBody = buildMessageBody(arguments);
        Status response;
        
        if (log().isDebugEnabled()) {
            log().debug("Dispatching microblog notification for user '" + svc.getUserId() + "' at base URL '" + svc.getBaseURL() + "' with message '" + messageBody + "'");
        }
        try {
            response = svc.updateStatus(messageBody);
        } catch (TwitterException e) {
            log().error("Microblog notification failed");
            log().info("Failed to update status for user '" + svc.getUserId() + "' at service URL '" + svc.getBaseURL() + "', caught exception: " + e.getMessage());
            return 1;
        }
        
        log().info("Microblog notification succeeded: update posted with ID " + response.getId());
        return 0;
    }
    
    protected Twitter buildUblogService(List<Argument> arguments) {
        MicroblogProfile profile = null;
        String serviceUrl = "";
        String authenUser = "";
        String authenPass = "";
        
        // First try to get a microblog profile called "notifd", falling back to the default if that fails
        profile = m_microblogConfigurationDao.getProfile("notifd");
        if (profile == null)
            profile = m_microblogConfigurationDao.getDefaultProfile();

        if (profile == null) {
            log().fatal("Unable to find a microblog profile called '" + UBLOG_PROFILE_NAME + "', and default profile does not exist; we cannot send microblog notifications!");
            throw new RuntimeException("Could not find a usable microblog profile.");
        }
        
        log().info("Using microblog profile with name '" + profile.getName() + "'");
        
        serviceUrl = profile.getServiceUrl();
        authenUser = profile.getAuthenUsername();
        authenPass = profile.getAuthenPassword();

        if (authenUser == null || "".equals(authenUser))
            log().warn("Working with a blank username, perhaps you forgot to set this in the microblog configuration?");
        if (authenPass == null || "".equals(authenPass))
            log().warn("Working with a blank password, perhaps you forgot to set this in the microblog configuration?");
        if (serviceUrl == null || "".equals(serviceUrl))
            throw new IllegalArgumentException("Cannot use a blank microblog service URL, perhaps you forgot to set this in the microblog configuration?");
        
        Twitter svc = new Twitter();
        svc.setBaseURL(serviceUrl);
        svc.setSource("OpenNMS");
        svc.setUserId(authenUser);
        svc.setPassword(authenPass);
        return svc;
    }
    
    protected String buildMessageBody(List<Argument> arguments) {
        String messageBody = "";
        
        for (Argument arg : arguments) {
            if (NotificationManager.PARAM_TEXT_MSG.equals(arg.getSwitch())) {
                messageBody = arg.getValue();
            }
        }
        
        if (messageBody == null) {
            // FIXME We should have a better Exception to use here for configuration problems
            throw new IllegalArgumentException("No message specified, but is required");
        }
        
        // Collapse whitespace in final message
        messageBody.replaceAll("\\s+", " ");
        if (log().isDebugEnabled()) {
            log().debug("Final message body after collapsing whitespace is: '" + messageBody + "'");
        }

        return messageBody;
    }

    protected ThreadCategory log() {
        return ThreadCategory.getInstance(getClass());
    }
    
    protected String findDestName(List<Argument> arguments) {
        for (Argument arg : arguments) {
            if (NotificationManager.PARAM_MICROBLOG_USERNAME.equals(arg.getSwitch())) {
                log().debug("Found destination microblog name: " + arg.getSwitch());
                return arg.getValue();
            }
        }
        log().debug("No destination microblog name found");
        return null;
    }
    
    protected static Resource findDefaultConfigResource() throws IOException {
        File configFile = ConfigFileConstants.getFile(ConfigFileConstants.MICROBLOG_CONFIG_FILE_NAME);
        return new FileSystemResource(configFile);        
    }

    public void setMicroblogConfigurationDao(MicroblogConfigurationDao dao) {
        m_microblogConfigurationDao = dao;
    }
    
    public MicroblogConfigurationDao getMicroblogConfigurationDao() {
        return m_microblogConfigurationDao;
    }
}

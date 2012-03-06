/*
 *  soapUI, copyright (C) 2004-2012 smartbear.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */

package com.eviware.soapui.impl.wsdl.panels.testcase;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.SoapUISystemProperties;
import com.eviware.soapui.impl.support.actions.ShowOnlineHelpAction;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.support.Tools;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.components.JXToolBar;
import com.eviware.soapui.support.components.NativeBrowserComponent;
import com.eviware.soapui.testondemand.DependencyValidator;
import com.eviware.soapui.testondemand.Location;
import com.eviware.soapui.testondemand.TestOnDemandCaller;
import com.eviware.x.dialogs.Worker.WorkerAdapter;
import com.eviware.x.dialogs.XProgressDialog;
import com.eviware.x.dialogs.XProgressMonitor;
import com.google.common.base.Strings;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * 
 * Panel for displaying a Test On Demand report
 * 
 */
public class TestOnDemandPanel extends JPanel
{
	// FIXME This should be a URL on our servers. Replace with the real URL when it has been developed by our web dev team.
	private static final String FIRST_PAGE_URL = "";

	private static final String GET_MORE_LOCATIONS_URL = "http://www2.smartbear.com/AlertSite_Monitor_APIs_Learn_More.html";
	private static final String GET_MORE_LOCATIONS_MESSAGE = "Get more locations...";

	private static final String INITIALIZING_MESSAGE = "Initializing...";

	private static final String NO_LOCATIONS_FOUND_MESSAGE = "No locations found";

	private static final String COULD_NOT_GET_LOCATIONS_MESSAGE = "Could not get Test On Demand Locations. Check your network connection.";
	private static final String COULD_NOT_UPLOAD_MESSAGE = "Could not upload TestCase to the selected location";

	private static final String UPLOAD_TEST_CASE_HEADING = "Upload TestCase";
	private static final String UPLOADING_TEST_CASE_MESSAGE = "Uploading TestCase..";

	@NonNull
	private JComboBox<Object> locationsComboBox;

	@NonNull
	private CustomNativeBrowserComponent browser;

	@NonNull
	private Action runAction;

	@NonNull
	private static List<Location> locationsCache = new ArrayList<Location>();

	private final WsdlTestCase testCase;

	protected DependencyValidator validator;

	public TestOnDemandPanel( WsdlTestCase testCase )
	{
		super( new BorderLayout() );

		this.testCase = testCase;
		setBackground( Color.WHITE );
		setOpaque( true );

		setValidator();

		add( buildToolbar(), BorderLayout.NORTH );

		initializeLocationsCache();

		if( !SoapUI.isJXBrowserDisabled( true ) )
		{
			browser = new CustomNativeBrowserComponent( true, false );
			add( browser.getComponent(), BorderLayout.CENTER );
		}
		else
		{
			JEditorPane jxbrowserDisabledPanel = new JEditorPane();
			jxbrowserDisabledPanel.setText( "Browser component disabled or not available on this platform" );
			add( jxbrowserDisabledPanel, BorderLayout.CENTER );
		}
	}

	protected void setValidator()
	{
		validator = new DependencyValidator();
	}

	public void release()
	{
		if( browser != null )
		{
			browser.release();
		}
	}

	private Component buildToolbar()
	{
		JXToolBar toolbar = UISupport.createToolbar();

		runAction = new RunAction();
		runAction.setEnabled( false );

		locationsComboBox = buildInitializingLocationsComboBox();
		locationsComboBox.addActionListener( new GetMoreLocationsAction() );

		toolbar.addFixed( UISupport.createToolbarButton( runAction ) );
		toolbar.addRelatedGap();
		toolbar.addFixed( locationsComboBox );
		toolbar.addGlue();
		toolbar.addFixed( UISupport.createToolbarButton( new ShowOnlineHelpAction( HelpUrls.ALERT_SITE_HELP_URL ) ) );

		return toolbar;
	}

	private JComboBox<Object> buildInitializingLocationsComboBox()
	{
		JComboBox<Object> initLocationsComboBox = new JComboBox<Object>();
		initLocationsComboBox.setPreferredSize( new Dimension( 150, 10 ) );
		initLocationsComboBox.addItem( INITIALIZING_MESSAGE );
		initLocationsComboBox.setEnabled( false );
		return initLocationsComboBox;
	}

	private void initializeLocationsCache()
	{
		if( locationsCache.isEmpty() )
		{
			new TestOnDemandCallerThread().start();
		}
		else
		{
			populateLocationsComboBox();
		}
	}

	private void populateLocationsComboBox()
	{
		locationsComboBox.removeAllItems();

		if( locationsCache.isEmpty() )
		{
			locationsComboBox.addItem( NO_LOCATIONS_FOUND_MESSAGE );
			openInInternalBrowser( SoapUI.PUSH_PAGE_ERROR_URL );
		}
		else
		{
			for( Location location : locationsCache )
			{
				locationsComboBox.addItem( location );
			}

			locationsComboBox.addItem( GET_MORE_LOCATIONS_MESSAGE );

			locationsComboBox.setEnabled( true );
			runAction.setEnabled( true );

			openInInternalBrowser( getFirstPageURL() );
		}

		invalidate();
	}

	// FIXME These guys should probably go in a utils class

	private void openURLSafely( String url )

	{
		if( SoapUI.isJXBrowserDisabled( true ) )
		{
			Tools.openURL( url );
		}
		else
		{
			if( browser != null )
			{
				browser.navigate( url, null );
			}
		}
	}

	private void openInInternalBrowser( String url )
	{
		if( SoapUI.isJXBrowserDisabled( false ) && browser != null )
		{
			browser.navigate( url, null );
		}
	}

	private String getFirstPageURL()
	{
		return System.getProperty( SoapUISystemProperties.TEST_ON_DEMAND_FIRST_PAGE_URL, FIRST_PAGE_URL );
	}

	private String getMoreLocationsURL()
	{
		return System.getProperty( SoapUISystemProperties.TEST_ON_DEMAND_GET_LOCATIONS_URL, GET_MORE_LOCATIONS_URL );
	}

	private class RunAction extends AbstractAction
	{
		public RunAction()
		{
			putValue( SMALL_ICON, UISupport.createImageIcon( "/run.gif" ) );
			putValue( Action.SHORT_DESCRIPTION, "Run Test On Demand report" );
		}

		public void actionPerformed( ActionEvent arg0 )
		{

			if( validator != null && !validator.isValid( testCase ) )
			{
				UISupport.showErrorMessage( "Your project contains external dependencies that "
						+ "are not supported by the Test-On-Demand functionality at this point." );
				return;
			}

			if( locationsComboBox != null )
			{
				Location selectedLocation = ( Location )locationsComboBox.getSelectedItem();

				XProgressDialog progressDialog = UISupport.getDialogs().createProgressDialog( UPLOAD_TEST_CASE_HEADING, 3,
						UPLOADING_TEST_CASE_MESSAGE, false );
				SendTestCaseWorker sendTestCaseWorker = new SendTestCaseWorker( testCase, selectedLocation );
				try
				{
					progressDialog.run( sendTestCaseWorker );
				}
				catch( Exception e )
				{
					SoapUI.logError( e );
				}

				String redirectUrl = sendTestCaseWorker.getResult();
				if( !Strings.isNullOrEmpty( redirectUrl ) )
				{
					openURLSafely( redirectUrl );
				}
			}
		}
	}

	private class GetMoreLocationsAction implements ActionListener
	{
		@Override
		public void actionPerformed( ActionEvent e )
		{
			if( locationsComboBox.getSelectedItem() != null
					&& locationsComboBox.getSelectedItem().equals( GET_MORE_LOCATIONS_MESSAGE ) )
			{
				openURLSafely( getMoreLocationsURL() );
				runAction.setEnabled( false );
			}
			else
			{
				if( locationsComboBox.isEnabled() && !runAction.isEnabled() )
				{
					openInInternalBrowser( getFirstPageURL() );
					runAction.setEnabled( true );
				}
			}
		}
	}

	private class CustomNativeBrowserComponent extends NativeBrowserComponent
	{
		public CustomNativeBrowserComponent( boolean addToolbar, boolean addStatusBar )
		{
			super( addToolbar, addStatusBar );
		}

		// TODO check if clicking a link opens a new window
	}

	private class SendTestCaseWorker extends WorkerAdapter
	{
		private final WsdlTestCase testCase;
		private final Location selectedLocation;
		private String result = null;

		public SendTestCaseWorker( WsdlTestCase testCase, Location selectedLocation )
		{
			this.testCase = testCase;
			this.selectedLocation = selectedLocation;
		}

		public Object construct( XProgressMonitor monitor )
		{
			try
			{
				TestOnDemandCaller caller = new TestOnDemandCaller();
				result = caller.sendTestCase( testCase, selectedLocation );
			}
			catch( Exception e )
			{
				SoapUI.logError( e );
				UISupport.showErrorMessage( COULD_NOT_UPLOAD_MESSAGE );
			}
			return result;
		}

		public String getResult()
		{
			return result;
		}
	}

	// Used to prevent soapUI from halting while waiting for the Test On Demand server to respond
	private class TestOnDemandCallerThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				locationsCache = new TestOnDemandCaller().getLocations();
			}
			catch( Exception e )
			{
				SoapUI.logError( e, COULD_NOT_GET_LOCATIONS_MESSAGE );
			}
			finally
			{
				populateLocationsComboBox();
			}
		}
	}
}

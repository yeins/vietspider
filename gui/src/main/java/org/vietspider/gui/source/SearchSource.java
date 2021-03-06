/***************************************************************************
 * Copyright 2003-2007 by VietSpider - All rights reserved.                *    
 **************************************************************************/
package org.vietspider.gui.source;

import java.util.ArrayList;
import java.util.prefs.Preferences;

import net.sf.swtaddons.autocomplete.text.AutocompleteTextInput;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Text;
import org.vietspider.client.common.ClientConnector2;
import org.vietspider.client.common.OrganizationClientHandler;
import org.vietspider.client.common.source.SourcesClientHandler;
import org.vietspider.common.Application;
import org.vietspider.common.Install;
import org.vietspider.common.text.VietComparator;
import org.vietspider.common.util.Worker;
import org.vietspider.gui.creator.Creator;
import org.vietspider.ui.services.ClientLog;
import org.vietspider.ui.widget.ApplicationFactory;
import org.vietspider.ui.widget.UIDATA;
import org.vietspider.ui.widget.waiter.WaitLoading;
import org.vietspider.user.AccessChecker;

/**
 *  Author : Nhu Dinh Thuan
 *          Email:nhudinhthuan@yahoo.com
 * Aug 1, 2007
 */
public class SearchSource extends SourcesHandler {

  private Text txtSearch;
  private Combo cboType;
  private AutocompleteTextInput txtAutoComplete;
  private int searchType = 0;
  private Creator creator;
  
  private List listSources;

  public SearchSource(ApplicationFactory factory, Composite parent) {
    super(parent);

    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 1;    
    setLayout(gridLayout);

    GridData gridData;
    cboType = factory.createCombo(this, SWT.READ_ONLY);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    cboType.setLayoutData(gridData);   
    cboType.addSelectionListener(new SelectionAdapter() {
      @SuppressWarnings("unused")
      public void widgetSelected(SelectionEvent e) {
        if(searchType == cboType.getSelectionIndex()) return;
        searchType = cboType.getSelectionIndex();
        Preferences prefs = Preferences.userNodeForPackage(SearchSource.class);
        prefs.put("searchType", String.valueOf(searchType));
        setValues(group, null);
      }
    });
    cboType.setItems(factory.getLabel("searchType").split(","));
    cboType.setFont(UIDATA.FONT_9);

    Preferences prefs = Preferences.userNodeForPackage(getClass());
    try {
      searchType = Integer.parseInt(prefs.get("searchType", ""));
    } catch (Exception e) {
      searchType = 0;
    }
    cboType.select(searchType);
    
    txtSearch = new Text(this, SWT.BORDER | SWT.SINGLE);
    gridData = new GridData(GridData.FILL_HORIZONTAL);
    txtSearch.setLayoutData(gridData);   
    txtSearch.addKeyListener(new KeyListener() {
      public void keyPressed(KeyEvent evt) {
        if(evt.keyCode == 13) search(null);
      }
      @SuppressWarnings("unused")
      public void keyReleased(KeyEvent evt) {        
      }
    });
    txtSearch.addSelectionListener(new SelectionAdapter(){
      @SuppressWarnings("unused")
      public void widgetSelected(SelectionEvent e) {
        search(null);
      }
    });
    txtSearch.setFont(UIDATA.FONT_9);
    
    listSources = factory.createList(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
    listSources.setFont(UIDATA.FONT_9);
    if(Application.LICENSE == Install.PERSONAL) listSources.setEnabled(false);
    gridData = new GridData(GridData.FILL_BOTH);
    listSources.setLayoutData(gridData);
    createMenu(listSources, factory);
    listSources.addSelectionListener(new SelectionAdapter() {
      @SuppressWarnings("unused")
      public void widgetSelected(SelectionEvent e) {
        if(listSources.getSelectionCount() < 1) return;
        creator.getInfoControl().getSourceEditor().reset();
        String category = null;
        String name = null;
        if(searchType == 0) {
          category = listSources.getSelection()[0];
          name = txtSearch.getText().trim();
        } else {
          String value = listSources.getSelection()[0];
//          value = value.substring(0, value.indexOf('.'));
          int idx = value.indexOf('.');
          category = value.substring(0, idx);
          name = value.substring(idx+1).trim();
        }
        creator.setSource(null, category, name, true);
      }
    });
    
  }

  public void search(final String category) {
    listSources.removeAll();
    Worker excutor = new Worker() {

      private String value;
      private String [] items;
      private AccessChecker accessChecker;
      
      public void abort() {
        ClientConnector2.currentInstance().abort();
      }

      public void before() {
        group = creator.getSelectedGroupName();
        value = txtSearch.getText();
      }

      public void execute() {
        try {
          accessChecker = new OrganizationClientHandler().loadAccessChecker();
          if(searchType == 0) {
            items = new SourcesClientHandler(group).getCategories(value);
          } else {
            value = value.trim().toLowerCase();
            items = new SourcesClientHandler(group).searchSourceByHost(value);
          }
        }catch (Exception e) {
          ClientLog.getInstance().setException(null, e);
        }
      }

      public void after() {
        if(items == null) return ;
        java.util.List<String> filterItem = new ArrayList<String>();
      	for(String item: items){
      		if(!accessChecker.isPermitAccess(item, true)) continue;
    			filterItem.add(item);
      	}
      	items = filterItem.toArray(new String[]{});
      	java.util.Arrays.sort(items, new VietComparator());
        listSources.setItems(items);
        listSources.setToolTipText(items.length+" items");
        if(searchType == 0 && category != null) {
          listSources.setSelection(new String []{category});
        }
      }
    };
    WaitLoading loading = new WaitLoading(listSources, excutor);
    loading.open();
  }

  String[] getSelectedCategories() {
    if(searchType == 0) return listSources.getSelection();
    
    String [] values = listSources.getSelection();
    for(int i = 0; i < values.length; i++) {
      int idx = values[i].indexOf('.');
      if(idx < 1) {
        values[i] = null; 
      } else {
        values[i] = values[i].substring(0, idx);
      }
    }
    return values;
  }

  public String getSelectedCategory() {
    String [] categories = getSelectedCategories();
    return categories == null || categories.length < 1 ? null : categories[0];
  }

  public String[] getSelectedSources() {
    if(searchType == 0) {
      if(listSources.getItemCount() < 1) return null;
      return new String[]{txtSearch.getText()};
    }
    
    String [] values = listSources.getSelection();
    for(int i = 0; i < values.length; i++) {
      int idx1 = values[i].indexOf('.');
      if(idx1 < 1) {
        values[i] = null; 
      } else {
        values[i] = values[i].substring(idx1+1).trim();
      }
    }
    return values;
  }

  String[] getSources(String category) {
    try {
      return new SourcesClientHandler(group).loadSources(category);
    }catch (Exception e) {
      ClientLog.getInstance().setException(getShell(), e);
      return null;
    }
  }
  
  public void setValues(final String group, final String category, final String...sources) {
    Worker excutor = new Worker() {

      private String [] items;
      private AccessChecker accessChecker;
      
      public void abort() {
        ClientConnector2.currentInstance().abort();
      }

      public void before() {
        if(group != null) {
          SearchSource.this.setGroup(group);
        }
      }

      public void execute() {
        try {
          accessChecker = new OrganizationClientHandler().loadAccessChecker();
          if(searchType == 0) {
            if(group == null)  {
              items = new SourcesClientHandler(SearchSource.this.group).loadSources();
            } else {
              items = new SourcesClientHandler(group).loadSources();
            }
          } else {
            items = new String[0];
          }
        } catch (Exception e) {
          ClientLog.getInstance().setException(null, e);
        }
      }

      public void after() {
        java.util.List<String> filterItem = new ArrayList<String>();
        if(items == null) return;
        for(String item: items){
          if(!accessChecker.isPermitAccess(item, true)) continue;
          filterItem.add(item);
        }
        if(txtAutoComplete == null) {
          txtAutoComplete = new AutocompleteTextInput(txtSearch, filterItem.toArray(new String[]{}));
        } else {
          txtAutoComplete.setSelectionItems(filterItem.toArray(new String[]{}));
        }
        
        if(searchType != 0) return;
        if(sources != null && sources.length > 0 && sources[0] != null) {
          txtSearch.setText(sources[0]);
        }
        search(category);
      }
    };
    WaitLoading loading = new WaitLoading(listSources, excutor);
    loading.open();
  }

  public void setCreator(Creator creator) { this.creator = creator; }
  public Creator getCreator() { return creator; }

  @SuppressWarnings("unused")
  public void setCategories(AccessChecker accessChecker, String[] categories) {
    setValues(group, null);
  }

  @SuppressWarnings("unused")
  public void setSelectedCategory(String cate) {
  }

  @SuppressWarnings("unused")
  public void setSelectedSources(Worker[]plugins, String... sources) {
  }

  @SuppressWarnings("unused")
  public void setSources(String... sources) {
  }

  @Override
  @SuppressWarnings("unused")
  public void setDisableSources(String... sources) {
  }
  
}

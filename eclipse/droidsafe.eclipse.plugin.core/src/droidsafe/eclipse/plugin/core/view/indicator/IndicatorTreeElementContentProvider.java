package droidsafe.eclipse.plugin.core.view.indicator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import droidsafe.eclipse.plugin.core.filters.Filter;
import droidsafe.eclipse.plugin.core.filters.FilterPred;
import droidsafe.eclipse.plugin.core.specmodel.TreeElement;
import droidsafe.eclipse.plugin.core.view.DroidsafeInfoTreeElementContentProvider;

/**
 * Content provider for the tree structure of the points-to outline view.
 * 
 * @author Limei Gilham (gilham@kestrel.edu)
 * 
 */
public class IndicatorTreeElementContentProvider extends DroidsafeInfoTreeElementContentProvider {
    
    /** The object on which the droidsafe analysis info is to be displayed in the outline view */
    protected JsonObject fInput;

    private IndicatorViewPart viewPart;

    public Object[] getRootElements() {
        Object[] rootElements = viewPart.getRootElements();
        if (rootElements == null) {
            rootElements = initializeRoots();
            viewPart.setRootElements(rootElements);
        }
        return rootElements;
    }
    
    public void setRootElements(Object[] rootElements) {
        viewPart.setRootElements(rootElements);
    }

    public IndicatorTreeElementContentProvider(IndicatorViewPart viewPart) {
        this.viewPart = viewPart;
    }

    @Override
    public Object[] getElements(Object input) {
        if (input instanceof JsonObject) {
            return getRootElements();
        } 
        return NO_CHILDREN;
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        this.fViewer = (TreeViewer) viewer;
        this.fInput = (JsonObject) newInput;
    }

    /**
     * Populate the tree elements of the JSON object outline view. Return the root elements.
     */
    protected Object[] initializeRoots() {
        getTreeElementMap().clear();
        List<TreeElement<JsonElement, JsonElement>> roots = new ArrayList<TreeElement<JsonElement, JsonElement>>();
        JsonArray rootArray = Utils.getChildrenArray(fInput);
        if (rootArray != null) {
            for (int i = 0; i < rootArray.size(); i++) {
                JsonElement rootElement = rootArray.get(i);
                if (!Utils.isEmptyJsonObject(rootElement)) {
                    List<TreeElement<JsonElement, JsonElement>> visibleRoots = initializeTree(rootElement);
                    roots.addAll(visibleRoots);
                }
            }
            roots = applyFilters(roots);
            return roots.toArray();
        }
        return NO_CHILDREN;
    }

    private List<TreeElement<JsonElement, JsonElement>> initializeTree(JsonElement jsonElement) {
        List<TreeElement<JsonElement, JsonElement>> result = new ArrayList<TreeElement<JsonElement, JsonElement>>();
        boolean visible = isVisible(jsonElement);
        TreeElement<JsonElement, JsonElement> element = null;
        if (visible) {
            element = new TreeElement<JsonElement, JsonElement>(jsonElement.toString(), jsonElement, JsonElement.class);
            getTreeElementMap().put(jsonElement, element);
            result.add(element);
        }
        JsonArray childrenArray = Utils.getChildrenArray(jsonElement);
        if (childrenArray != null) {
            for (int i = 0; i < childrenArray.size(); i++) {
                JsonElement childElement = childrenArray.get(i);
                if (!Utils.isEmptyJsonObject(childElement)) {
                    List<TreeElement<JsonElement, JsonElement>> children = initializeTree(childElement);
                    if (visible) {
                        for (TreeElement<JsonElement, JsonElement> child: children) {
                            element.addChild(child);
                        }
                    } else {
                        result.addAll(children);
                    }
                }
            }
        }
        return result;
    }
    
    private List<TreeElement<JsonElement, JsonElement>> applyFilters(
            List<TreeElement<JsonElement, JsonElement>> roots) {
        for (Filter filter: viewPart.getFilters()) {
            if (filter.isEnabled()) {
                roots = applyFilter(roots, filter);
            }
        }
        return roots;
    }

    private List<TreeElement<JsonElement, JsonElement>> applyFilter(
            List<TreeElement<JsonElement, JsonElement>> roots, Filter filter) {
        List<TreeElement<JsonElement, JsonElement>> results = 
                new ArrayList<TreeElement<JsonElement, JsonElement>>();
        for (TreeElement<JsonElement, JsonElement> root: roots) {
            if (!isFiltered(root, filter)) {
                applyFilterToChildren(root, filter);
                results.add(root);
            }
        }
        return results;
    }

    private void applyFilterToChildren(TreeElement<JsonElement, JsonElement> element, Filter filter) {
        List<TreeElement<JsonElement, ?>> toRemove = new ArrayList<TreeElement<JsonElement, ?>>();
        for (TreeElement<JsonElement, ?> child : element.getChildren()) {
            if (isFiltered(child, filter)) {
                toRemove.add(child);
            } else {
                applyFilterToChildren((TreeElement<JsonElement, JsonElement>)child, filter);
            }
        }
        for (TreeElement<JsonElement, ?> child : toRemove) {
            element.removeChild(child);
        }
    }

    private boolean isFiltered(TreeElement<JsonElement, ?> element, Filter filter) {
        JsonElement jsonElt = element.getData();
        FilterPred pred = filter.pred;
        switch (filter.op) {
            case EXCLUDE: return pred.apply(jsonElt);
            case INCLUDE: return !pred.apply(jsonElt);
            default: // SHOW
                return !satisfiedInTree(element, pred);
        }
    }

    private boolean satisfiedInTree(TreeElement<JsonElement, ?> element, FilterPred pred) {
        JsonElement jsonElt = element.getData();
        boolean satisfied = pred.apply(jsonElt);
        if (satisfied)
            return true;
        for (TreeElement<?,?> child: element.getChildren()) {
            if (satisfiedInTree((TreeElement<JsonElement, ?>)child, pred))
                return true;
        }
        return false;
    }

    private boolean isVisible(JsonElement jsonElement) {
        Map<String, Boolean> visibilityMap = viewPart.getVisibilityMap();
        String type = Utils.getObjectType(jsonElement);
        if (type != null && visibilityMap.containsKey(type)) {
            return visibilityMap.get(type).booleanValue();
        }
        return true;
    }
    
    /**
     * Reset the content of this content provider.
     */
    @Override
    protected void reset() {
    }


}

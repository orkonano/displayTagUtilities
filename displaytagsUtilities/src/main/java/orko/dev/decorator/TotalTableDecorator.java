package orko.dev.decorator;

import java.lang.reflect.Method;
import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.jsp.PageContext;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.displaytag.decorator.DisplaytagColumnDecorator;
import org.displaytag.decorator.TableDecorator;
import org.displaytag.exception.DecoratorException;
import org.displaytag.exception.ObjectLookupException;
import org.displaytag.model.HeaderCell;
import org.displaytag.model.TableModel;
import org.displaytag.util.LookupUtil;

import orko.dev.decorator.util.Grouper;
import orko.dev.decorator.util.GrouperType;
import orko.dev.decorator.util.Totalization;
import orko.dev.reflection.ReflectionUtils;


/**
 * 
 * @author orko
 *
 */
public class TotalTableDecorator extends TableDecorator {

	/**
	 * Logger.
	 */
	private static Log log = LogFactory.getLog(TotalTableDecorator.class);

	/**
	 * total amount.
	 */
	private Map<String,Double> grandTotals = new HashMap<String,Double>();

	/**
	 * total amount for current group.
	 */
	private Map<String,Double> subTotals = new HashMap<String,Double>();

	/**
	 * Previous values needed for grouping.
	 */
	private Map previousValues = new HashMap();

	/**
	 * Name of the property used for grouping.
	 */
	private String groupPropertyName;
	
	private String groupTypePropertyName;

	/**
	 * Label used for subtotals. Default: "{0} total".
	 */
	private String subtotalLabel = "{0} subtotal";

	/**
	 * Label used for totals. Default: "Total".
	 */
	private String totalLabel = "Total";
	
	private Format formatTotals = NumberFormat.getCurrencyInstance();



	/**
	 * Setter for <code>subtotalLabel</code>.
	 * 
	 * @param subtotalLabel
	 *            The subtotalLabel to set.
	 */
	public void setSubtotalLabel(String subtotalLabel) {
		this.subtotalLabel = subtotalLabel;
	}

	/**
	 * Setter for <code>totalLabel</code>.
	 * 
	 * @param totalLabel
	 *            The totalLabel to set.
	 */
	public void setTotalLabel(String totalLabel) {
		this.totalLabel = totalLabel;
	}

	/**
	 * @see org.displaytag.decorator.Decorator#init(PageContext, Object,
	 *      TableModel)
	 */
	public void init(PageContext context, Object decorated, TableModel tableModel) {
		super.init(context, decorated, tableModel);

		// reset
		groupPropertyName = null;
		groupTypePropertyName = null;
		grandTotals.clear();
		subTotals.clear();
		previousValues.clear();

		 if (this.getClass().isAnnotationPresent(Grouper.class)){
			 Grouper aGrouper = ((Grouper)this.getClass().getAnnotation(Grouper.class));
			 groupPropertyName = aGrouper.propertyEvaluationName();
			 groupTypePropertyName = aGrouper.propertyTypeName();
		 }
	}

	public String startRow() {
		String subtotalRow = null;
		GrouperType grouperType = GrouperType.ADD;
		if (groupPropertyName != null) {
			Object groupedPropertyValue = evaluate(groupPropertyName);
			Object previousGroupedPropertyValue = previousValues.get(groupPropertyName);
			// subtotals
			if (previousGroupedPropertyValue != null&& !ObjectUtils.equals(previousGroupedPropertyValue,groupedPropertyValue)) {
				subtotalRow = createTotalRow(false);
			}
			previousValues.put(groupPropertyName, groupedPropertyValue);
			grouperType = (GrouperType)evaluate(groupTypePropertyName);
		}

		for (Iterator it = tableModel.getHeaderCellList().iterator(); it .hasNext();) {
			HeaderCell cell = (HeaderCell) it.next();
			if (this.isTotalCell(cell)) {
				Number amount = getAmountCellTotal(cell);
				String totalPropertyName = cell.getBeanPropertyName();
				Number previousSubTotal = (Number) subTotals.get(totalPropertyName);
				Number previousGrandTotals = (Number) grandTotals .get(totalPropertyName);
				subTotals .put(totalPropertyName, new Double( (previousSubTotal != null ? previousSubTotal .doubleValue() : 0) + (amount != null ? amount .doubleValue() : 0)));
				if (grouperType.equals(GrouperType.ADD)){
					grandTotals .put(totalPropertyName, new Double( (previousGrandTotals != null ? previousGrandTotals .doubleValue() : 0) + (amount != null ? amount .doubleValue() : 0)));
				}else{
					grandTotals .put(totalPropertyName, new Double( (previousGrandTotals != null ? previousGrandTotals .doubleValue() : 0) - (amount != null ? amount .doubleValue() : 0)));
				}
				
			}
		}

		return subtotalRow;
	}

	private Number getAmountCellTotal(HeaderCell cell) {
		String totalPropertyName = cell.getBeanPropertyName();
		try {
			Totalization aTotalization = ReflectionUtils.getMethodProperty(this, totalPropertyName).getAnnotation(Totalization.class);
			Number amount = null;
			if (aTotalization.owner()){
				amount = (Number) evaluate(totalPropertyName);
			}else{
				amount = (Number) evaluate(aTotalization.propertyTotalLinked());
			}
			return amount;
		} catch (NoSuchMethodException | SecurityException e) {
			log.fatal(e.getMessage(), e);
		}
		return null;
	}

	private boolean isTotalCell(HeaderCell cell) {
		String totalPropertyName = cell.getBeanPropertyName();
		Method m = null;
		try {
			m = ReflectionUtils.getMethodProperty(this, totalPropertyName);
		} catch (NoSuchMethodException | SecurityException e) {
			return false;
		}
		return m.isAnnotationPresent(Totalization.class);
	}

	@Override
	protected Object evaluate(String propertyName) {
		Object result= null;
		try{
			result = super.evaluate(propertyName);
		}catch (Exception e) {
			result = null;
		}
		if (result == null){
			try
	        {
	            result = LookupUtil.getBeanProperty(this, propertyName);
	        }
	        catch (Exception e)
	        {
	            result = null;
	        }
		}
		return result;
	}

	/**
	 * After every row completes we evaluate to see if we should be drawing a
	 * new total line and summing the results from the previous group.
	 * 
	 * @return String
	 */
	public final String finishRow() {
		StringBuilder buffer = new StringBuilder();

		// Grand totals...
		if (getViewIndex() == ((List) getDecoratedObject()).size() - 1) {
			if (groupPropertyName != null) {
				buffer.append(createTotalRow(false));
			}
			buffer.append(createTotalRow(true));
		}
		return buffer.toString();

	}

	protected String createTotalRow(boolean grandTotal) {
		StringBuilder buffer = new StringBuilder(1000);
		buffer.append("\n<tr class=\"total\">");

		List<HeaderCell> headerCells = tableModel.getHeaderCellList();

		for (Iterator<HeaderCell> it = headerCells.iterator(); it.hasNext();) {
			HeaderCell cell =  it.next();
			String cssClass = ObjectUtils.toString(cell.getHtmlAttributes() .get("class"));

			buffer.append("<td"); 
			if (StringUtils.isNotEmpty(cssClass)) {
				buffer.append(" class=\""); //$NON-NLS-1$
				buffer.append(cssClass);
				buffer.append("\""); //$NON-NLS-1$
			}
			buffer.append(">"); //$NON-NLS-1$

			if (this.isTotalCell(cell)) {
				String totalPropertyName = cell.getBeanPropertyName();
				Object total = grandTotal ? grandTotals.get(totalPropertyName) : subTotals.get(totalPropertyName);

				DisplaytagColumnDecorator[] decorators = cell .getColumnDecorators();
				for (int j = 0; j < decorators.length; j++) {
					try {
						total = decorators[j].decorate(total, this.getPageContext(), tableModel.getMedia());
					} catch (DecoratorException e) {
						log.warn(e.getMessage(), e);
						// ignore, use undecorated value for totals
					}
				}
				buffer.append(this.formatTotals.format(total));
			} else if (groupPropertyName != null && cell.getColumnNumber() == 0) {
				buffer.append(grandTotal ? totalLabel : MessageFormat.format( subtotalLabel, new Object[] { previousValues.get(groupPropertyName) }));
			}
			buffer.append("</td>"); 
		}

		buffer.append("</tr>"); //$NON-NLS-1$
		// reset subtotal
		this.subTotals.clear();
		return buffer.toString();
	}

	public Format getFormatTotals() {
		return formatTotals;
	}

	public void setFormatTotals(Format formatTotals) {
		this.formatTotals = formatTotals;
	}

}

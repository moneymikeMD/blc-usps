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
package org.broadleafcommerce.vendor.usps.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import javax.annotation.Resource;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.broadleafcommerce.common.money.Money;
import org.broadleafcommerce.common.vendor.service.exception.FulfillmentPriceException;
import org.broadleafcommerce.core.catalog.domain.Sku;
import org.broadleafcommerce.core.order.domain.BundleOrderItem;
import org.broadleafcommerce.core.order.domain.DiscreteOrderItem;
import org.broadleafcommerce.core.order.domain.FulfillmentGroup;
import org.broadleafcommerce.core.order.domain.FulfillmentGroupItem;
import org.broadleafcommerce.core.order.domain.FulfillmentOption;
import org.broadleafcommerce.core.order.domain.OrderItem;
import org.broadleafcommerce.core.order.service.type.FulfillmentType;
import org.broadleafcommerce.core.pricing.service.fulfillment.provider.FulfillmentEstimationResponse;
import org.broadleafcommerce.core.pricing.service.fulfillment.provider.FulfillmentPricingProvider;
import org.broadleafcommerce.profile.core.domain.Address;
import org.broadleafcommerce.vendor.usps.domain.USPSConfiguration;
import org.broadleafcommerce.vendor.usps.domain.USPSFulfillmentOption;
import org.broadleafcommerce.vendor.usps.domain.type.USPSServiceType;
import org.broadleafcommerce.vendor.usps.gateway.USPSPricingGateway;
import org.broadleafcommerce.vendor.usps.service.USPSConfigurationService;

import com.usps.webtools.rates.PostageV4Type;
import com.usps.webtools.rates.RateV4ResponseType;
import com.usps.webtools.rates.ResponsePackageV4Type;

public class USPSFulfillmentPricingProvider implements FulfillmentPricingProvider {
    
    private static final Log LOG = LogFactory.getLog(USPSFulfillmentPricingProvider.class);
    
    private static final String DEFAULT_CURRENCY = "USD";

    @Resource(name="blUSPSPricingGateway")
    protected USPSPricingGateway uspsPricingGateway;
    
    @Resource(name="blUSPSConfigurationService")
    protected USPSConfigurationService uspsConfigurationService;

    private Comparator<PostageV4Type> priceComparator = new Comparator<PostageV4Type>() {
        @Override
        public int compare(PostageV4Type o1, PostageV4Type o2) {
            return Float.compare(o1.getRate(), o2.getRate());
        }
    };

    @Override
    public FulfillmentGroup calculateCostForFulfillmentGroup(
            FulfillmentGroup fulfillmentGroup) throws FulfillmentPriceException {
        if (! canCalculateCostForFulfillmentGroup(fulfillmentGroup, fulfillmentGroup.getFulfillmentOption())) {
            throw new FulfillmentPriceException("USPSFulfillmentPricingProvider could not calculate fulfillment for fulfillment option: " + fulfillmentGroup.getFulfillmentOption().getId());
        }
        Money totalFees = null;
        RateV4ResponseType rateResponse = null;
        USPSFulfillmentOption uspsFulfillmentOption = (USPSFulfillmentOption)fulfillmentGroup.getFulfillmentOption();
        
        //Look up USPS Configuration
        USPSConfiguration config = uspsConfigurationService.findUSPSConfiguration();
        if (config == null) {
            throw new FulfillmentPriceException("There was no USPS Configuration record found. Could not call USPS API.");
        }
        
        if (fulfillmentGroup.getFulfillmentOption().getUseFlatRates()) {
            BigDecimal totalAmount = BigDecimal.ZERO;
            ArrayList<FulfillmentGroupItem> itemsToPrice = new ArrayList<FulfillmentGroupItem>();
            
            List<FulfillmentGroupItem> fgItems = fulfillmentGroup.getFulfillmentGroupItems();
            for (FulfillmentGroupItem fgItem : fgItems) {
                OrderItem orderItem = fgItem.getOrderItem();
                if (orderItem instanceof DiscreteOrderItem) {
                    Sku sku = ((DiscreteOrderItem)orderItem).getSku();
                    if (sku.getFulfillmentFlatRates() != null && sku.getFulfillmentFlatRates().get(uspsFulfillmentOption) != null) {
                        totalAmount = totalAmount.add(sku.getFulfillmentFlatRates().get(uspsFulfillmentOption).multiply(new BigDecimal(fgItem.getQuantity())));
                    } else {
                        itemsToPrice.add(fgItem);
                    }
                } else if (orderItem instanceof BundleOrderItem) {
                    List<DiscreteOrderItem> discreteItems = ((BundleOrderItem)orderItem).getDiscreteOrderItems();
                    for (DiscreteOrderItem discreteItem : discreteItems) {
                        Sku sku = discreteItem.getSku();
                        if (sku.getFulfillmentFlatRates() != null && sku.getFulfillmentFlatRates().get(uspsFulfillmentOption) != null) {
                            totalAmount = totalAmount.add(sku.getFulfillmentFlatRates().get(uspsFulfillmentOption).multiply(new BigDecimal(fgItem.getQuantity())));
                        } else {
                            itemsToPrice.add(fgItem);
                        }
                    }
                } else {
                    throw new FulfillmentPriceException("Not able to calculate fulfillment pricing for order item of type: " + orderItem.getClass().getName() 
                            + " Should be a DiscreteOrderItem or a BundleOrderItem");
                }
            }
            
            if (itemsToPrice.isEmpty()) {
                //There were no skus that could be priced via USPS
                totalFees = new Money(totalAmount);
            } else {
                rateResponse = uspsPricingGateway.retrieveDomesticRates(fulfillmentGroup, fgItems, config, false);
                List<ResponsePackageV4Type> packages = rateResponse.getPackage();
                for (ResponsePackageV4Type pkg : packages) {
                    List<PostageV4Type> postages = pkg.getPostage();
                    for (PostageV4Type postage : postages) {
                        if (uspsFulfillmentOption.getService().getName().equals(postage.getMailService())) {
                            totalFees = new Money(totalAmount.add(new BigDecimal(postage.getRate())), DEFAULT_CURRENCY);
                            break;
                        }
                    }
                }
            }
            
        } else {
            rateResponse = uspsPricingGateway.retrieveDomesticRates(fulfillmentGroup, fulfillmentGroup.getFulfillmentGroupItems(), config, false);
            List<ResponsePackageV4Type> packages = rateResponse.getPackage();
            LOG.debug("Debuging packages...");
            for (ResponsePackageV4Type pkg : packages) {
                LOG.debug(pkg.toString());
                List<PostageV4Type> postages = pkg.getPostage();
                Collections.sort(postages, priceComparator);
                for (PostageV4Type postage : postages) {
                    if(doesMatchMailService(uspsFulfillmentOption.getService(), postage.getMailService())) {
                        totalFees = new Money(postage.getRate(), DEFAULT_CURRENCY);
                        break;
                    }
                }
            }
        }
        if (totalFees == null) {
            throw new FulfillmentPriceException("There was no price found for the given pricing option and fulfillment group.");
        }
        
        if (config.getUpchargePercentage() != null) {
            BigDecimal upCharge = config.getUpchargePercentage();
            upCharge = upCharge.setScale(2, RoundingMode.HALF_UP);
            if (upCharge.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalAmount = totalFees.getAmount().setScale(2, RoundingMode.HALF_UP);
                totalAmount = totalAmount.add(totalAmount.multiply(upCharge));
                totalFees = new Money(totalAmount, totalFees.getCurrency());
            } else {
                throw new IllegalStateException("USPS Upcharge must be positive. Found: " + upCharge.toString());
            }
        }
        
        fulfillmentGroup.setRetailShippingPrice(totalFees);
        fulfillmentGroup.setSaleShippingPrice(totalFees);
        fulfillmentGroup.setRetailFulfillmentPrice(totalFees);
        fulfillmentGroup.setShippingPrice(totalFees);
        
        return fulfillmentGroup;
    }

    protected boolean doesMatchMailService(USPSServiceType serviceType, String mailService) {
        if(serviceType.getName().equals(mailService)) {
            return true;
        } else if(serviceType.getPattern() != null) {
            return serviceType.getPattern().matcher(mailService).matches();
        }

        return false;
    }

    @Override
    public FulfillmentEstimationResponse estimateCostForFulfillmentGroup(
            FulfillmentGroup fulfillmentGroup, Set<FulfillmentOption> options)
            throws FulfillmentPriceException {
        //Look up USPS Configuration
        USPSConfiguration config = uspsConfigurationService.findUSPSConfiguration();
        if (config == null) {
            throw new FulfillmentPriceException("There was not USPS Configuration record found. Could not call USPS API.");
        }
        
        FulfillmentEstimationResponse response = new FulfillmentEstimationResponse();
        HashMap<FulfillmentOption, Money> shippingPrices = new HashMap<FulfillmentOption, Money>();
        response.setFulfillmentOptionPrices(shippingPrices);

        //final boolean anyFulfillmentOptionViable = options.stream().anyMatch(option -> canCalculateCostForFulfillmentGroup(fulfillmentGroup, option));

        boolean anyFulfillmentOptionViable = false;
        for(FulfillmentOption option : options) {
            if(canCalculateCostForFulfillmentGroup(fulfillmentGroup, option)) {
                anyFulfillmentOptionViable = true;
                break;
            }
        }

        if(anyFulfillmentOptionViable) {

            RateV4ResponseType rateResponse = uspsPricingGateway.retrieveDomesticRates(fulfillmentGroup, fulfillmentGroup.getFulfillmentGroupItems(), config, true);
            List<ResponsePackageV4Type> packages = rateResponse.getPackage();

            for (FulfillmentOption option : options) {
                if (canCalculateCostForFulfillmentGroup(fulfillmentGroup, option)) {
                    USPSFulfillmentOption uspsOption = (USPSFulfillmentOption) option;
                    for (ResponsePackageV4Type type : packages) {
                        if (type.getError() == null) {
                            List<PostageV4Type> postages = type.getPostage();
                            Collections.sort(postages, priceComparator);
                            for (PostageV4Type postage : postages) {
                                if (doesMatchMailService(uspsOption.getService(), postage.getMailService())) {
                                    BigDecimal totalAmount = new BigDecimal(postage.getRate());
                                    totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
                                    if (config.getUpchargePercentage() != null) {
                                        BigDecimal upCharge = config.getUpchargePercentage();
                                        upCharge = upCharge.setScale(2, RoundingMode.HALF_UP);
                                        if (upCharge.doubleValue() > 0D) {
                                            totalAmount = totalAmount.add(totalAmount.multiply(upCharge));
                                        } else {
                                            throw new IllegalStateException("USPS Upcharge must be positive. Found: " + upCharge.toString());
                                        }
                                    }

                                    shippingPrices.put(option, new Money(totalAmount, DEFAULT_CURRENCY));
                                    break;
                                }
                            }

                        }
                    }
                }
            }
        }
        return response;
    }

    protected boolean isCountryValid(FulfillmentGroup fulfillmentGroup) {
        Address address = fulfillmentGroup.getAddress();
        String abbreviation = "";
        if(address.getIsoCountryAlpha2() != null) {
            abbreviation = address.getIsoCountryAlpha2().getAlpha2();
        } else if(address.getCountry() != null) {
            abbreviation = address.getCountry().getAbbreviation();
        } else if(address.getIsoCountrySubdivision() != null) {
            abbreviation = address.getIsoCountrySubdivision().substring(0, 2);
        }
        return "US".equals(abbreviation) || "MI".equals(abbreviation);
    }

    @Override
    public boolean canCalculateCostForFulfillmentGroup(
            FulfillmentGroup fulfillmentGroup, FulfillmentOption option) {
        //no address associated with the fulfillment group, can't calculate it
        if (fulfillmentGroup.getAddress() == null || !isCountryValid(fulfillmentGroup)) {
            return false;
        }
        
        //If the currency is not US, we can't price it (for now). This may change when we implement international shipping with USPS.
        if (! DEFAULT_CURRENCY.equals(fulfillmentGroup.getOrder().getCurrency().getCurrencyCode())) {
            return false;
        }
        
        //If this is not a USPSFulfillmentOption, then we can't calculate it.
        //If the FulfillmentGroup is not a PHYSICAL fulfillment group, then we can't calculate it.
        if (option instanceof USPSFulfillmentOption && fulfillmentGroup.getType() != null) {
            if (fulfillmentGroup.getType().equals(FulfillmentType.PHYSICAL_SHIP) || fulfillmentGroup.getType().equals(FulfillmentType.PHYSICAL_PICKUP_OR_SHIP)) {
                //If there are any SKUs in this fulfillment group that cannot be fulfilled with this fulfillment option, 
                //then we can't calculate it.
                List<FulfillmentGroupItem> fgItems = fulfillmentGroup.getFulfillmentGroupItems();
                for (FulfillmentGroupItem fgItem : fgItems) {
                    OrderItem orderItem = fgItem.getOrderItem();
                    if (orderItem instanceof DiscreteOrderItem) {
                        Sku sku = ((DiscreteOrderItem)orderItem).getSku();
                        List<FulfillmentOption> excludedFulfillmentOptions = sku.getExcludedFulfillmentOptions();
                        if (excludedFulfillmentOptions != null && ! excludedFulfillmentOptions.isEmpty()) {
                            for (FulfillmentOption skuFO : excludedFulfillmentOptions) {
                                if (option.equals(skuFO)) {
                                    return false;
                                }
                            }
                        }
                    } else if (orderItem instanceof BundleOrderItem) {
                        BundleOrderItem bundleItem = (BundleOrderItem)orderItem;
                        List<DiscreteOrderItem> discreteOrderItems = bundleItem.getDiscreteOrderItems();
                        for (DiscreteOrderItem discreteItem : discreteOrderItems) {
                            Sku sku = discreteItem.getSku();
                            List<FulfillmentOption> excludedFulfillmentOptions = sku.getExcludedFulfillmentOptions();
                            if (excludedFulfillmentOptions != null && ! excludedFulfillmentOptions.isEmpty()) {
                                for (FulfillmentOption skuFO : excludedFulfillmentOptions) {
                                    if (option.equals(skuFO)) {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
                return true;
            }
        }
        String fulfillmentType = (fulfillmentGroup.getType() == null) ? null : fulfillmentGroup.getType().getType();
        LOG.debug("USPS not calculating shipments for FulfillmentGroup with option class: " + option.getClass() + " and type: " + fulfillmentType);
        
        return false;
    }

    public void setUspsPricingGateway(USPSPricingGateway gateway) {
        this.uspsPricingGateway = gateway;
    }
    
    public void setUspsConfigurationService(USPSConfigurationService service) {
        this.uspsConfigurationService = service;
    }
}

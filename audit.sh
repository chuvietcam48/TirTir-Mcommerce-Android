#!/bin/bash
FILES=(
  "app/src/main/res/layout/activity_login.xml"
  "app/src/main/res/layout/activity_register.xml"
  "app/src/main/res/layout/activity_main.xml"
  "app/src/main/res/menu/bottom_nav_menu.xml"
  "app/src/main/res/layout/activity_splash.xml"
  "app/src/main/res/layout/activity_onboarding.xml"
  "app/src/main/res/layout/fragment_profile.xml"
  "app/src/main/res/layout/fragment_home.xml"
  "app/src/main/res/layout/item_product.xml"
  "app/src/main/res/layout/activity_product_detail.xml"
  "app/src/main/res/layout/fragment_cart.xml"
  "app/src/main/res/layout/item_cart.xml"
  "app/src/main/res/layout/activity_checkout.xml"
  "app/src/main/res/layout/activity_order_success.xml"
  "app/src/main/res/layout/fragment_order_history.xml"
  "app/src/main/res/layout/item_order.xml"
  "app/src/main/res/layout/activity_address_manager.xml"
  "app/src/main/res/layout/activity_wishlist.xml"
  "app/src/main/res/layout/item_wishlist.xml"
)

for file in "${FILES[@]}"; do
  if [ -f "$file" ]; then
    echo "EXISTS: $file"
  else
    echo "MISSING: $file"
  fi
done

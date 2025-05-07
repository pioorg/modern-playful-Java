/*
 * Copyright 2025 Marit van Dijk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.przybyl;

public class DiscountCalculator {
    int memberDiscountPercentage(boolean isGoldMember) {
        return switch (isGoldMember) {
            case true -> 20;
            case false -> 5;
        };
    }

    int itemDiscountPercentage(int items) {
        return switch (items) {
            case 2 -> 5;
            case 3, 4 -> 10;
            case int i when i >= 5 -> 20;
            default -> 0;
        };
    }
}
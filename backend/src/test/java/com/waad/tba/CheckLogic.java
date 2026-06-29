package com.waad.tba;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import com.waad.tba.modules.member.service.MemberDuplicateService;
import com.waad.tba.modules.member.dto.MemberDuplicateGroupDto;

import java.util.List;

@SpringBootApplication
public class CheckLogic {
    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(CheckLogic.class, args);
        MemberDuplicateService service = ctx.getBean(MemberDuplicateService.class);
        
        System.out.println("--- Finding Duplicates ---");
        List<MemberDuplicateGroupDto> duplicates = service.findDuplicates();
        for (MemberDuplicateGroupDto group : duplicates) {
            if (group.getNormalizedName().contains("عبدالمنعم")) {
                System.out.println("FOUND: " + group.getNormalizedName() + " (size: " + group.getMembers().size() + ") - Parent: " + group.getParentId());
            }
        }
        System.out.println("--- Done ---");
        System.exit(0);
    }
}
